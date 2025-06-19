const fs = require('fs');
const path = require('path');

// Define the new modular file structure
const fileStructure = [
    // Application Layer
    'src/main/java/com/cvmaker/application/CVMakerApplication.java',
    'src/main/java/com/cvmaker/application/CVGenerationOrchestrator.java',
    'src/main/java/com/cvmaker/application/CoverLetterOrchestrator.java',

    // Domain Layer - CV
    'src/main/java/com/cvmaker/domain/cv/CVGenerator.java',
    'src/main/java/com/cvmaker/domain/cv/CVContent.java',
    'src/main/java/com/cvmaker/domain/cv/CVTemplate.java',

    // Domain Layer - Cover Letter
    'src/main/java/com/cvmaker/domain/coverletter/CoverLetterGenerator.java',
    'src/main/java/com/cvmaker/domain/coverletter/CoverLetterContent.java',

    // Domain Layer - Shared
    'src/main/java/com/cvmaker/domain/shared/ContentAnalyzer.java',
    'src/main/java/com/cvmaker/domain/shared/JobDescription.java',

    // Infrastructure Layer - AI
    'src/main/java/com/cvmaker/infrastructure/ai/OpenAIService.java',
    'src/main/java/com/cvmaker/infrastructure/ai/AIPromptBuilder.java',
    'src/main/java/com/cvmaker/infrastructure/ai/AIResponseParser.java',

    // Infrastructure Layer - LaTeX
    'src/main/java/com/cvmaker/infrastructure/latex/LaTeXCompiler.java',
    'src/main/java/com/cvmaker/infrastructure/latex/LaTeXValidator.java',

    // Infrastructure Layer - FileSystem
    'src/main/java/com/cvmaker/infrastructure/filesystem/FileSystemService.java',
    'src/main/java/com/cvmaker/infrastructure/filesystem/TemplateRepository.java',

    // Infrastructure Layer - Console
    'src/main/java/com/cvmaker/infrastructure/console/ConsoleProgressReporter.java',
    'src/main/java/com/cvmaker/infrastructure/console/ConsoleUIService.java',

    // Infrastructure Layer - Progress
    'src/main/java/com/cvmaker/infrastructure/progress/ProgressReporter.java',
    'src/main/java/com/cvmaker/infrastructure/progress/ProgressEvent.java',

    // Configuration
    'src/main/java/com/cvmaker/config/ApplicationSettings.java',
    'src/main/java/com/cvmaker/config/SettingsBuilder.java',
    'src/main/java/com/cvmaker/config/SettingsValidator.java',

    // Shared - Exceptions
    'src/main/java/com/cvmaker/shared/exceptions/CVGenerationException.java',
    'src/main/java/com/cvmaker/shared/exceptions/TemplateException.java',
    'src/main/java/com/cvmaker/shared/exceptions/AIServiceException.java',

    // Shared - Validation
    'src/main/java/com/cvmaker/shared/validation/ValidationResult.java',
    'src/main/java/com/cvmaker/shared/validation/Validator.java',

    // Service Layer (if needed)
    'src/main/java/com/cvmaker/service/ServiceContainer.java',
    'src/main/java/com/cvmaker/service/AsyncTaskExecutor.java',
    'src/main/java/com/cvmaker/service/RetryService.java'
];

function createFile(filePath) {
    const dir = path.dirname(filePath);

    // Create directory if it doesn't exist
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
        console.log(`📁 Created directory: ${dir}`);
    }

    // Create empty file if it doesn't exist
    if (!fs.existsSync(filePath)) {
        fs.writeFileSync(filePath, '');
        console.log(`📄 Created file: ${filePath}`);
    } else {
        console.log(`⚠️  File already exists: ${filePath}`);
    }
}

function main() {
    console.log('🚀 Creating modular file structure...\n');

    let createdFiles = 0;
    let existingFiles = 0;

    fileStructure.forEach(filePath => {
        if (!fs.existsSync(filePath)) {
            createFile(filePath);
            createdFiles++;
        } else {
            console.log(`⚠️  File already exists: ${filePath}`);
            existingFiles++;
        }
    });

    console.log('\n✅ File structure creation completed!');
    console.log(`📊 Summary:`);
    console.log(`   • Created: ${createdFiles} files`);
    console.log(`   • Already existed: ${existingFiles} files`);
    console.log(`   • Total: ${fileStructure.length} files`);

    if (createdFiles > 0) {
        console.log('\n💡 Next steps:');
        console.log('   1. Start implementing the classes based on the design suggestions');
        console.log('   2. Move existing code from old classes to new structure');
        console.log('   3. Add proper package declarations and imports');
        console.log('   4. Implement interfaces and dependency injection');
    }
}

main();