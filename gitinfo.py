import requests
import getpass
import os
import base64
from pathlib import Path
import time
import random
import logging
from requests.exceptions import ConnectionError, Timeout, TooManyRedirects, RequestException

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("github_downloader.log"),
        logging.StreamHandler()
    ]
)

# File extensions to include (only code files)
CODE_EXTENSIONS = {
    '.py', '.java', '.js', '.jsx', '.ts', '.tsx', '.html', '.css', '.scss', '.sass',
    '.less', '.php', '.rb', '.go', '.c', '.cpp', '.h', '.hpp', '.cs', '.swift',
    '.kt', '.rs', '.sh', '.bash', '.sql', '.vue', '.jsx', '.tsx', '.xml', '.yaml',
    '.yml', '.json', '.md', '.scala', '.pl', '.pm', '.asm', '.s', '.f', '.f90',
    '.r', '.dart', '.lua', '.groovy', '.ps1', '.psm1', '.bat', '.cmd', '.hs', '.erl'
}

def get_github_repositories():
    """Authenticate with GitHub and list user repositories"""
    # Get GitHub personal access token
    token = getpass.getpass("Enter your GitHub personal access token: ")
    
    # Set up authentication headers
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    # Get authenticated user info
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
    
    # Get repositories for the authenticated user
    repos_url = f"https://api.github.com/users/{username}/repos"
    all_repos = []
    page = 1
    
    while True:
        params = {"page": page, "per_page": 100}
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
            logging.error(f"Error fetching repositories page {page}: {str(e)}")
            break
    
    # Print repository information
    logging.info(f"Found {len(all_repos)} repositories for {username}:")
    for i, repo in enumerate(all_repos, 1):
        logging.info(f"{i}. {repo['name']}")
        logging.info(f"   Description: {repo['description'] or 'No description'}")
        logging.info(f"   Stars: {repo['stargazers_count']}")
    
    return username, all_repos, headers

def make_request_with_retry(url, max_retries=5, base_delay=1, headers=None, params=None):
    """Make a request with exponential backoff retry logic"""
    retries = 0
    while retries < max_retries:
        try:
            response = requests.get(url, headers=headers, params=params, timeout=30)
            
            # Handle rate limiting
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
                logging.warning(f"Request failed with status code {response.status_code}: {response.text}")
                if response.status_code >= 500:  # Server errors
                    retries += 1
                    delay = base_delay * (2 ** retries) + random.uniform(0, 1)
                    logging.info(f"Retrying in {delay:.2f} seconds... (Attempt {retries}/{max_retries})")
                    time.sleep(delay)
                else:
                    # Client errors except rate limiting are not retried
                    return None
                    
        except (ConnectionError, Timeout, TooManyRedirects, RequestException) as e:
            retries += 1
            if retries >= max_retries:
                logging.error(f"Max retries reached for {url}. Last error: {str(e)}")
                return None
                
            delay = base_delay * (2 ** retries) + random.uniform(0, 1)
            logging.info(f"Connection error: {str(e)}. Retrying in {delay:.2f} seconds... (Attempt {retries}/{max_retries})")
            time.sleep(delay)
    
    return None

def get_repository_contents(repo_name, username, headers, path=""):
    """Get contents of repository at the specified path"""
    contents_url = f"https://api.github.com/repos/{username}/{repo_name}/contents/{path}"
    
    try:
        response = make_request_with_retry(contents_url, headers=headers)
        if not response:
            logging.error(f"Failed to fetch repository contents for {path}")
            return []
        
        return response.json()
    except Exception as e:
        logging.error(f"Error getting contents for {path}: {str(e)}")
        return []

def save_file_content(file_path, content, username_folder, repo_name, all_repos_file=None):
    """Save file content to the appropriate text file and optionally to ALL_REPOS.txt"""
    try:
        # Create the output text file path
        output_file = Path(username_folder) / f"{repo_name}.txt"
        
        # Append the file content to the repo text file
        with open(output_file, "a", encoding="utf-8", errors="replace") as f:
            f.write(f"===== {file_path} =======\n")
            f.write(content)
            f.write("\n\n")
        
        # If all_repos_file is provided, also append to it
        if all_repos_file:
            with open(all_repos_file, "a", encoding="utf-8", errors="replace") as f:
                f.write(f"===== {repo_name}/{file_path} =======\n")
                f.write(content)
                f.write("\n\n")
    except Exception as e:
        logging.error(f"Error saving file content for {file_path}: {str(e)}")

def process_repository(repo, username, headers, all_repos_file):
    """Process all code files from the repository and save to text file"""
    repo_name = repo['name']
    logging.info(f"Processing repository: {repo_name}")
    
    # Create username folder if it doesn't exist
    username_folder = Path(username)
    username_folder.mkdir(exist_ok=True)
    
    try:
        # Create/clear the repository text file
        repo_file = username_folder / f"{repo_name}.txt"
        with open(repo_file, "w", encoding="utf-8") as f:
            f.write(f"Repository: {repo_name}\n")
            f.write(f"Owner: {username}\n")
            f.write(f"URL: {repo['html_url']}\n")
            f.write(f"Description: {repo['description'] or 'No description'}\n")
            f.write("=" * 50 + "\n\n")
        
        # Add repository header to ALL_REPOS.txt
        with open(all_repos_file, "a", encoding="utf-8") as f:
            f.write(f"\n\n{'=' * 80}\n")
            f.write(f"Repository: {repo_name}\n")
            f.write(f"Owner: {username}\n")
            f.write(f"URL: {repo['html_url']}\n")
            f.write(f"Description: {repo['description'] or 'No description'}\n")
            f.write("=" * 80 + "\n\n")
        
        # Process files recursively
        process_path_contents(repo_name, username, headers, "", username_folder, all_repos_file)
        
        logging.info(f"Repository '{repo_name}' successfully saved to '{repo_file}'")
        return True
    except Exception as e:
        logging.error(f"Error processing repository {repo_name}: {str(e)}")
        return False

def process_path_contents(repo_name, username, headers, path, username_folder, all_repos_file):
    """Recursively process contents of a path in the repository"""
    contents = get_repository_contents(repo_name, username, headers, path)
    
    for item in contents:
        try:
            if item['type'] == 'dir':
                # Recursively process directory contents
                process_path_contents(repo_name, username, headers, item['path'], username_folder, all_repos_file)
            else:
                # Process file if it's a code file
                file_path = item['path']
                file_ext = Path(file_path).suffix.lower()
                
                if file_ext in CODE_EXTENSIONS:
                    process_file(item, username_folder, headers, repo_name, all_repos_file)
                else:
                    logging.info(f"Skipping non-code file: {file_path}")
        except Exception as e:
            logging.error(f"Error processing path item {item.get('path', 'unknown')}: {str(e)}")

def process_file(file_info, username_folder, headers, repo_name, all_repos_file):
    """Process a single file from the repository"""
    file_path = file_info['path']
    
    # Skip large files
    if file_info.get('size', 0) > 1000000:  # Files larger than ~1MB
        content = f"[Large file: {file_info.get('size', 'unknown')} bytes - content not included]"
        save_file_content(file_path, content, username_folder, repo_name, all_repos_file)
        logging.info(f"Skipped large file: {file_path}")
        return
    
    # Get file content
    try:
        if 'download_url' in file_info and file_info['download_url']:
            response = make_request_with_retry(file_info['download_url'], headers=headers)
            if response:
                try:
                    content = response.text
                    save_file_content(file_path, content, username_folder, repo_name, all_repos_file)
                    logging.info(f"Processed: {file_path}")
                except UnicodeDecodeError:
                    # For binary files, note that it's binary
                    content = f"[Binary file: {file_info.get('size', 'unknown')} bytes - content not included]"
                    save_file_content(file_path, content, username_folder, repo_name, all_repos_file)
                    logging.info(f"Noted binary file: {file_path}")
            else:
                logging.warning(f"Failed to download {file_path}")
        else:
            # For some files, we need to use the content from the API response
            response = make_request_with_retry(file_info['url'], headers=headers)
            if response:
                content_data = response.json()
                if 'content' in content_data and content_data.get('encoding') == 'base64':
                    try:
                        decoded_content = base64.b64decode(content_data['content']).decode('utf-8')
                        save_file_content(file_path, decoded_content, username_folder, repo_name, all_repos_file)
                        logging.info(f"Processed: {file_path}")
                    except UnicodeDecodeError:
                        # For binary files, note that it's binary
                        content = f"[Binary file: {file_info.get('size', 'unknown')} bytes - content not included]"
                        save_file_content(file_path, content, username_folder, repo_name, all_repos_file)
                        logging.info(f"Noted binary file: {file_path}")
                else:
                    logging.warning(f"Unable to decode content for {file_path}")
            else:
                logging.warning(f"Failed to get content for {file_path}")
    except Exception as e:
        logging.error(f"Error processing file {file_path}: {str(e)}")

def main():
    try:
        username, all_repos, headers = get_github_repositories()
        if not all_repos:
            logging.error("No repositories found or authentication failed.")
            return
        
        logging.info(f"Preparing to process all {len(all_repos)} repositories (code files only)...")
        logging.info(f"Including files with extensions: {', '.join(sorted(CODE_EXTENSIONS))}")
        
        # Ask for confirmation before processing all repos
        confirm = input(f"Process all {len(all_repos)} repositories? (y/n): ")
        if confirm.lower() != 'y':
            logging.info("Operation cancelled.")
            return
        
        # Create username folder if it doesn't exist
        username_folder = Path(username)
        username_folder.mkdir(exist_ok=True)
        
        # Create/clear the ALL_REPOS.txt file
        all_repos_file = username_folder / "ALL_REPOS.txt"
        with open(all_repos_file, "w", encoding="utf-8") as f:
            f.write(f"ALL REPOSITORIES FOR {username}\n")
            f.write(f"Created on: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"Total repositories: {len(all_repos)}\n")
            f.write("=" * 50 + "\n\n")
        
        # Process all repositories
        successful_repos = 0
        for i, repo in enumerate(all_repos, 1):
            logging.info(f"[{i}/{len(all_repos)}] Processing repository: {repo['name']}")
            success = process_repository(repo, username, headers, all_repos_file)
            if success:
                successful_repos += 1
            
            # Small delay between repositories to avoid rate limiting
            if i < len(all_repos):
                delay = random.uniform(2, 5)  # Random delay between 2-5 seconds
                logging.info(f"Waiting {delay:.2f} seconds before next repository...")
                time.sleep(delay)
        
        logging.info(f"\nProcessed {successful_repos} out of {len(all_repos)} repositories successfully!")
        logging.info(f"Individual repository files are in the '{username}' folder")
        logging.info(f"Combined repository data is in '{all_repos_file}'")
        logging.info(f"Check 'github_downloader.log' for detailed information")
        
    except Exception as e:
        logging.error(f"An unexpected error occurred: {str(e)}")

if __name__ == "__main__":
    main()