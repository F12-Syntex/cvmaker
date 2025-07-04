import requests
import getpass
import os
import base64
import json
from pathlib import Path
import time
import random
import logging
from requests.exceptions import ConnectionError, Timeout, TooManyRedirects, RequestException
from datetime import datetime
import openai 
from collections import defaultdict, Counter

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("github_skills_analyzer.log"),
        logging.StreamHandler()
    ]
)

# STRICT CODE FILE EXTENSIONS ONLY
STRICT_CODE_EXTENSIONS = {
    '.py', '.java', '.ts', '.tsx', '.cpp', '.c', '.h', '.hpp', '.cs', '.swift',
    '.kt', '.rs', '.go', '.php', '.rb', '.scala', '.pl', '.asm', '.s', '.f', 
    '.f90', '.r', '.dart', '.lua', '.groovy', '.hs', '.erl', '.clj', '.ml',
    '.elm', '.jl', '.nim', '.cr', '.zig', '.v', '.d', '.pas', '.ada', '.adb',
    '.ads', '.vhd', '.vhdl', '.vb', '.fs', '.fsx', '.m', '.mm', '.sol', '.move',
    '.js', '.jsx', '.vue', '.svelte'  # Added common web dev extensions
}

# Files and directories to always skip
SKIP_PATTERNS = {
    'node_modules', '.git', '.svn', '.hg', 'dist', 'build', 'target', 'bin', 'obj',
    '.gradle', '.idea', '.vscode', '__pycache__', '.pytest_cache', '.tox',
    'vendor', 'packages', '.nuget', 'bower_components', 'jspm_packages',
    '.DS_Store', 'Thumbs.db', '.env', '.env.local', '.env.production',
    'coverage', '.nyc_output', 'logs', 'tmp', 'temp', '.tmp', '.temp'
}

# Configuration for content limits to control costs
MAX_FILES_PER_REPO = 15  # Limit files analyzed per repo
MAX_FILE_SIZE = 5000  # Max characters per file to analyze
MAX_TOTAL_CONTENT = 8000  # Max total content sent to AI per repo

def get_openai_api_key():
    """Get OpenAI API key from environment variable"""
    api_key = os.getenv('OPENAI_API_KEY')
    if not api_key:
        logging.error("OPENAI_API_KEY environment variable not found")
        return None
    openai.api_key = api_key
    return api_key

def make_request_with_retry(url, max_retries=3, base_delay=1, headers=None, params=None):
    """Make a request with exponential backoff retry logic"""
    retries = 0
    while retries < max_retries:
        try:
            response = requests.get(url, headers=headers, params=params, timeout=30)
            
            if response.status_code == 403 and 'rate limit' in response.text.lower():
                reset_time = int(response.headers.get('X-RateLimit-Reset', 0))
                current_time = int(time.time())
                sleep_time = max(reset_time - current_time + 5, 60)
                logging.warning(f"Rate limit hit. Waiting for {sleep_time} seconds...")
                time.sleep(sleep_time)
                continue
                
            if response.status_code == 200:
                return response
            else:
                logging.warning(f"Request failed with status code {response.status_code}")
                if response.status_code >= 500:
                    retries += 1
                    delay = base_delay * (2 ** retries) + random.uniform(0, 1)
                    logging.info(f"Retrying in {delay:.2f} seconds...")
                    time.sleep(delay)
                else:
                    return None
                    
        except (ConnectionError, Timeout, TooManyRedirects, RequestException) as e:
            retries += 1
            if retries >= max_retries:
                logging.error(f"Max retries reached for {url}. Last error: {str(e)}")
                return None
                
            delay = base_delay * (2 ** retries) + random.uniform(0, 1)
            logging.info(f"Connection error. Retrying in {delay:.2f} seconds...")
            time.sleep(delay)
    
    return None

def get_github_repositories():
    """Authenticate with GitHub and list user repositories"""
    token = os.getenv('GITHUB_KEY')
    if not token:
        logging.error("GITHUB_KEY environment variable not found")
        return None, None, None
    
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    user_url = "https://api.github.com/user"
    try:
        user_response = make_request_with_retry(user_url, headers=headers)
        if not user_response:
            return None, None, None
    except Exception as e:
        logging.error(f"Authentication failed: {str(e)}")
        return None, None, None
    
    username = user_response.json()["login"]
    logging.info(f"Found GitHub username: {username}")
    
    repos_url = f"https://api.github.com/users/{username}/repos"
    all_repos = []
    page = 1
    
    while True:
        params = {"page": page, "per_page": 100, "sort": "updated", "direction": "desc"}
        try:
            repos_response = make_request_with_retry(repos_url, headers=headers, params=params)
            if not repos_response:
                break
                
            repos = repos_response.json()
            if not repos:
                break
                
            all_repos.extend(repos)
            page += 1
        except Exception as e:
            logging.error(f"Error fetching repositories: {str(e)}")
            break
    
    logging.info(f"Found {len(all_repos)} repositories")
    return username, all_repos, headers

def get_repository_contents(repo_name, username, headers, path=""):
    """Get contents of repository at the specified path"""
    contents_url = f"https://api.github.com/repos/{username}/{repo_name}/contents/{path}"
    
    try:
        response = make_request_with_retry(contents_url, headers=headers)
        if not response:
            return []
        return response.json()
    except Exception as e:
        logging.error(f"Error getting contents for {path}: {str(e)}")
        return []

def should_skip_file(file_path):
    """Check if a file should be skipped"""
    file_name = Path(file_path).name.lower()
    
    # Skip test files, config files, etc.
    skip_keywords = ['test', 'spec', 'config', 'setup', 'build', 'dist', 'min.js', 'bundle']
    for keyword in skip_keywords:
        if keyword in file_name:
            return True
    
    return False

def should_skip_directory(dir_path):
    """Check if a directory should be skipped"""
    dir_name = Path(dir_path).name.lower()
    return dir_name in SKIP_PATTERNS

def extract_code_samples(repo_name, username, headers, max_files=MAX_FILES_PER_REPO):
    """Extract representative code samples from repository"""
    code_samples = []
    file_count = 0
    
    def process_directory(path=""):
        nonlocal file_count
        if file_count >= max_files or should_skip_directory(path):
            return
        
        contents = get_repository_contents(repo_name, username, headers, path)
        
        # Sort to prioritize main files
        contents.sort(key=lambda x: (
            x['type'] != 'file',  # Files first
            'main' not in x['name'].lower(),  # Files with 'main' first
            'index' not in x['name'].lower(),  # Then 'index' files
            x['name'].lower()  # Then alphabetical
        ))
        
        for item in contents:
            if file_count >= max_files:
                break
                
            try:
                if item['type'] == 'dir':
                    process_directory(item['path'])
                elif item['type'] == 'file':
                    file_path = item['path']
                    file_ext = Path(file_path).suffix.lower()
                    
                    if (file_ext in STRICT_CODE_EXTENSIONS and 
                        not should_skip_file(file_path) and
                        item.get('size', 0) < 50000):  # Skip very large files
                        
                        content = get_file_content(item, headers)
                        if content:
                            # Truncate content to limit size
                            if len(content) > MAX_FILE_SIZE:
                                content = content[:MAX_FILE_SIZE] + "\n... [truncated]"
                            
                            code_samples.append({
                                'path': file_path,
                                'extension': file_ext,
                                'content': content,
                                'size': len(content)
                            })
                            file_count += 1
                            
            except Exception as e:
                logging.error(f"Error processing {item.get('path', 'unknown')}: {str(e)}")
    
    process_directory()
    return code_samples

def get_file_content(file_info, headers):
    """Get content of a single file"""
    try:
        if 'download_url' in file_info and file_info['download_url']:
            response = make_request_with_retry(file_info['download_url'], headers=headers)
            if response:
                try:
                    return response.text
                except UnicodeDecodeError:
                    return None
        else:
            response = make_request_with_retry(file_info['url'], headers=headers)
            if response:
                content_data = response.json()
                if 'content' in content_data and content_data.get('encoding') == 'base64':
                    try:
                        return base64.b64decode(content_data['content']).decode('utf-8')
                    except UnicodeDecodeError:
                        return None
    except Exception as e:
        logging.error(f"Error getting file content: {str(e)}")
    return None

def analyze_repository_with_ai(repo_info, code_samples):
    """Use AI to analyze repository and extract skills/achievements"""
    try:
        # Prepare content for AI analysis
        repo_summary = f"""
Repository: {repo_info['name']}
Description: {repo_info.get('description', 'No description')}
Language: {repo_info.get('language', 'Multiple/Unknown')}
Stars: {repo_info.get('stargazers_count', 0)}
Forks: {repo_info.get('forks_count', 0)}
"""
        
        # Combine code samples with size limit
        code_content = ""
        total_size = 0
        
        for sample in code_samples:
            sample_text = f"\n--- {sample['path']} ---\n{sample['content']}\n"
            if total_size + len(sample_text) > MAX_TOTAL_CONTENT:
                break
            code_content += sample_text
            total_size += len(sample_text)
        
        if not code_content.strip():
            return {
                'skills': ['Repository analysis incomplete - no code samples available'],
                'achievements': ['Repository exists but content could not be analyzed'],
                'technologies': [],
                'summary': 'Unable to analyze repository content'
            }
        
        prompt = f"""
Analyze this GitHub repository and provide a concise summary of the developer's skills and achievements demonstrated in the code.

{repo_summary}

Code samples:
{code_content}

Please provide a JSON response with:
1. "technologies": List of technologies, frameworks, languages used
2. "skills": List of programming skills and techniques demonstrated  
3. "achievements": List of notable accomplishments or features implemented
4. "summary": Brief 2-3 sentence overview of what this project demonstrates

Focus on what the developer has accomplished and learned. Be specific about technical skills but keep it concise.
"""

        response = openai.ChatCompletion.create(
            model="gpt-3.5-turbo",  # Using cheaper model to control costs
            messages=[{"role": "user", "content": prompt}],
            max_tokens=800,  # Limit response size
            temperature=0.3
        )
        
        result_text = response.choices[0].message.content.strip()
        
        # Try to parse JSON response
        try:
            return json.loads(result_text)
        except json.JSONDecodeError:
            # If JSON parsing fails, create structured response from text
            return {
                'skills': ['AI analysis completed but format irregular'],
                'achievements': ['Repository analyzed with partial results'],
                'technologies': [],
                'summary': result_text[:200] + "..." if len(result_text) > 200 else result_text
            }
            
    except Exception as e:
        logging.error(f"Error in AI analysis: {str(e)}")
        return {
            'skills': ['Analysis failed - check API credentials and limits'],
            'achievements': ['Repository exists but analysis encountered errors'],
            'technologies': [],
            'summary': f'Analysis error: {str(e)}'
        }

def main():
    try:
        # Get API keys from environment variables
        logging.info("Loading API keys from environment variables...")
        openai_key = get_openai_api_key()
        if not openai_key:
            logging.error("OPENAI_API_KEY environment variable required")
            return
            
        username, all_repos, headers = get_github_repositories()
        if not all_repos:
            logging.error("No repositories found or authentication failed.")
            return
        
        logging.info(f"Found {len(all_repos)} repositories")
        
        # Ask user how many repos to analyze
        max_repos = input(f"How many repositories to analyze? (max {len(all_repos)}, enter for all): ")
        if max_repos.strip():
            try:
                max_repos = min(int(max_repos), len(all_repos))
                all_repos = all_repos[:max_repos]
            except ValueError:
                logging.info("Invalid number, analyzing all repositories")
        
        logging.info(f"Will analyze {len(all_repos)} repositories")
        logging.info(f"Cost control: Max {MAX_FILES_PER_REPO} files per repo, {MAX_FILE_SIZE} chars per file")
        
        confirm = input("Proceed with AI analysis? This will use OpenAI API credits (y/n): ")
        if confirm.lower() != 'y':
            logging.info("Operation cancelled.")
            return
        
        # Create output directory
        output_dir = Path(f"{username}_skills_analysis")
        output_dir.mkdir(exist_ok=True)
        
        # Initialize results
        all_skills = Counter()
        all_technologies = Counter()
        repo_analyses = []
        
        for i, repo in enumerate(all_repos, 1):
            logging.info(f"[{i}/{len(all_repos)}] Analyzing: {repo['name']}")
            
            try:
                # Extract code samples
                code_samples = extract_code_samples(repo['name'], username, headers)
                
                if not code_samples:
                    logging.warning(f"No code samples found for {repo['name']}")
                    continue
                
                logging.info(f"Found {len(code_samples)} code files to analyze")
                
                # Analyze with AI
                analysis = analyze_repository_with_ai(repo, code_samples)
                
                # Store results
                repo_analysis = {
                    'name': repo['name'],
                    'url': repo['html_url'],
                    'description': repo.get('description', ''),
                    'language': repo.get('language', ''),
                    'stars': repo.get('stargazers_count', 0),
                    'forks': repo.get('forks_count', 0),
                    'analysis': analysis
                }
                
                repo_analyses.append(repo_analysis)
                
                # Update counters
                for skill in analysis.get('skills', []):
                    all_skills[skill] += 1
                for tech in analysis.get('technologies', []):
                    all_technologies[tech] += 1
                
                # Save individual repo analysis
                repo_file = output_dir / f"{repo['name']}_analysis.json"
                with open(repo_file, 'w', encoding='utf-8') as f:
                    json.dump(repo_analysis, f, indent=2, ensure_ascii=False)
                
                logging.info(f"Completed analysis of {repo['name']}")
                
                # Add delay to avoid rate limits
                if i < len(all_repos):
                    time.sleep(random.uniform(2, 4))
                    
            except Exception as e:
                logging.error(f"Error analyzing {repo['name']}: {str(e)}")
                continue
        
        # Generate comprehensive summary
        summary_report = {
            'username': username,
            'analysis_date': datetime.now().isoformat(),
            'total_repositories': len(all_repos),
            'analyzed_repositories': len(repo_analyses),
            'top_skills': dict(all_skills.most_common(20)),
            'top_technologies': dict(all_technologies.most_common(15)),
            'repositories': repo_analyses
        }
        
        # Save comprehensive summary
        summary_file = output_dir / "SKILLS_SUMMARY.json"
        with open(summary_file, 'w', encoding='utf-8') as f:
            json.dump(summary_report, f, indent=2, ensure_ascii=False)
        
        # Create human-readable summary
        readable_summary = generate_readable_summary(summary_report)
        readable_file = output_dir / "SKILLS_SUMMARY.md"
        with open(readable_file, 'w', encoding='utf-8') as f:
            f.write(readable_summary)
        
        logging.info(f"\n{'='*50}")
        logging.info(f"Analysis Complete!")
        logging.info(f"Analyzed {len(repo_analyses)} repositories")
        logging.info(f"Results saved in '{output_dir}' folder:")
        logging.info(f"  - SKILLS_SUMMARY.json (detailed data)")
        logging.info(f"  - SKILLS_SUMMARY.md (readable summary)")
        logging.info(f"  - Individual repo analyses")
        logging.info(f"{'='*50}")
        
    except Exception as e:
        logging.error(f"An unexpected error occurred: {str(e)}")

def generate_readable_summary(summary_report):
    """Generate human-readable summary report"""
    md_content = f"""# GitHub Skills Analysis for {summary_report['username']}

**Analysis Date:** {summary_report['analysis_date'][:10]}  
**Repositories Analyzed:** {summary_report['analyzed_repositories']} of {summary_report['total_repositories']}

## Top Skills Demonstrated
"""
    
    for skill, count in summary_report['top_skills'].items():
        md_content += f"- **{skill}** (appeared in {count} repositories)\n"
    
    md_content += "\n## Top Technologies Used\n"
    
    for tech, count in summary_report['top_technologies'].items():
        md_content += f"- **{tech}** (used in {count} repositories)\n"
    
    md_content += "\n## Repository Analysis\n\n"
    
    for repo in summary_report['repositories']:
        md_content += f"### [{repo['name']}]({repo['url']})\n"
        if repo['description']:
            md_content += f"*{repo['description']}*\n\n"
        
        analysis = repo['analysis']
        
        if analysis.get('summary'):
            md_content += f"**Summary:** {analysis['summary']}\n\n"
        
        if analysis.get('technologies'):
            md_content += "**Technologies:** " + ", ".join(analysis['technologies']) + "\n\n"
        
        if analysis.get('skills'):
            md_content += "**Skills Demonstrated:**\n"
            for skill in analysis['skills']:
                md_content += f"- {skill}\n"
            md_content += "\n"
        
        if analysis.get('achievements'):
            md_content += "**Key Achievements:**\n"
            for achievement in analysis['achievements']:
                md_content += f"- {achievement}\n"
            md_content += "\n"
        
        md_content += "---\n\n"
    
    return md_content

if __name__ == "__main__":
    main()