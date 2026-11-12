package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class ApplicationConfigTest {

    private static Function<String, String> source(Map<String, String> values) {
        return values::get;
    }

    @Test
    void lookupPrefersPrimarySource() {
        String value = ApplicationConfig.lookup("KEY",
                source(Map.of("KEY", "from-dotenv")), source(Map.of("KEY", "from-env")));

        assertThat(value).isEqualTo("from-dotenv");
    }

    @Test
    void lookupFallsBackToEnvironmentWhenDotenvMissing() {
        String value = ApplicationConfig.lookup("KEY", source(Map.of()), source(Map.of("KEY", "from-env")));

        assertThat(value).isEqualTo("from-env");
    }

    @Test
    void lookupTreatsBlankAsMissingAndTrims() {
        assertThat(ApplicationConfig.lookup("KEY", source(Map.of("KEY", "   ")), source(Map.of("KEY", " v "))))
                .isEqualTo("v");
        assertThat(ApplicationConfig.lookup("KEY", source(Map.of("KEY", "")), source(Map.of()))).isNull();
    }

    @Test
    void requireValueRejectsMissingWithKeyInMessage() {
        assertThatThrownBy(() -> ApplicationConfig.requireValue("GOOGLE_CLIENT_ID", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID")
                .hasMessageContaining(".env");
        assertThatThrownBy(() -> ApplicationConfig.requireValue("GOOGLE_CLIENT_ID", "  "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireValueReturnsTrimmedValue() {
        assertThat(ApplicationConfig.requireValue("K", " abc ")).isEqualTo("abc");
    }

    @Test
    void requireOnUnknownKeyFails() {
        assertThatThrownBy(() -> ApplicationConfig.require("CVMAKER_TEST_KEY_THAT_IS_NEVER_SET_6f1c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CVMAKER_TEST_KEY_THAT_IS_NEVER_SET_6f1c");
    }

    @Test
    void getWithDefaultReturnsDefaultWhenUnset() {
        assertThat(ApplicationConfig.get("CVMAKER_TEST_KEY_THAT_IS_NEVER_SET_6f1c", "dflt")).isEqualTo("dflt");
    }

    @Test
    void dataDirectoryDefaultsToWorkingDirectory() {
        assertThat(ApplicationConfig.dataDirectory(null)).isEqualTo(Paths.get("."));
        assertThat(ApplicationConfig.dataDirectory("  ")).isEqualTo(Paths.get("."));
        assertThat(ApplicationConfig.dataDirectory("/var/data")).isEqualTo(Paths.get("/var/data"));
    }

    @Test
    void dataFilesKeepTheirHistoricNames() {
        assertThat(ApplicationConfig.getJobApplicationsDbFile().getFileName().toString())
                .isEqualTo(ApplicationConfig.JOB_APPLICATIONS_DB_FILE);
        assertThat(ApplicationConfig.getProcessedEmailsFile().getFileName().toString())
                .isEqualTo(ApplicationConfig.PROCESSED_EMAILS_FILE);
        assertThat(ApplicationConfig.getSystemStateFile().getFileName().toString())
                .isEqualTo(ApplicationConfig.SYSTEM_STATE_FILE);
    }
}
