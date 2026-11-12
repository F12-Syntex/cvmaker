package com.cvmaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.cvmaker.CVGenerator.CommandResult;
import com.cvmaker.CVGenerator.LatexCompilationException;
import com.cvmaker.CVGenerator.LatexCompiler;
import com.cvmaker.CVGenerator.ProcessCommandRunner;

class CVGeneratorTest {

    @Nested
    class FileNames {

        @Test
        void pdfNameReplacesOnlyTheTexSuffix() {
            assertThat(CVGenerator.pdfFileNameFor("cv.tex")).isEqualTo("cv.pdf");
            // The old String.replace(".tex", ".pdf") turned this into "my.pdf.notes.pdf"
            assertThat(CVGenerator.pdfFileNameFor("my.tex.notes.tex")).isEqualTo("my.tex.notes.pdf");
            assertThat(CVGenerator.pdfFileNameFor("cv.texfile.tex")).isEqualTo("cv.texfile.pdf");
            assertThat(CVGenerator.pdfFileNameFor("CV.TEX")).isEqualTo("CV.pdf");
            assertThat(CVGenerator.pdfFileNameFor("cv")).isEqualTo("cv.pdf");
        }

        @Test
        void sanitizeKeepsOrdinaryNames() {
            assertThat(CVGenerator.sanitizeFileName("cv.pdf", "x.pdf")).isEqualTo("cv.pdf");
            assertThat(CVGenerator.sanitizeFileName("software_engineer_acme", "job"))
                    .isEqualTo("software_engineer_acme");
        }

        @Test
        void sanitizeDropsDirectoriesAndTraversal() {
            assertThat(CVGenerator.sanitizeFileName("../../etc/passwd", "x")).isEqualTo("passwd");
            assertThat(CVGenerator.sanitizeFileName("C:\\Users\\me\\cv.pdf", "x")).isEqualTo("cv.pdf");
            assertThat(CVGenerator.sanitizeFileName("..", "fallback")).isEqualTo("fallback");
            assertThat(CVGenerator.sanitizeFileName(".hidden.pdf", "x")).isEqualTo("hidden.pdf");
        }

        @Test
        void sanitizeReplacesIllegalCharactersAndFallsBack() {
            assertThat(CVGenerator.sanitizeFileName("cv:1?*.pdf", "x")).isEqualTo("cv_1__.pdf");
            assertThat(CVGenerator.sanitizeFileName("a\nb.pdf", "x")).isEqualTo("a_b.pdf");
            assertThat(CVGenerator.sanitizeFileName(null, "fallback")).isEqualTo("fallback");
            assertThat(CVGenerator.sanitizeFileName("   ", "fallback")).isEqualTo("fallback");
            assertThat(CVGenerator.sanitizeFileName("dir/", "fallback")).isEqualTo("fallback");
        }

        @Test
        void baseNameStripsLastExtension() {
            assertThat(CVGenerator.getBaseName(Path.of("out", "cv.tex"))).isEqualTo("cv");
            assertThat(CVGenerator.getBaseName(Path.of("a.b.tex"))).isEqualTo("a.b");
            assertThat(CVGenerator.getBaseName(Path.of("noext"))).isEqualTo("noext");
        }
    }

    @Nested
    class Cleanup {

        @Test
        void deletesTrimmedExtensionsAndIgnoresBlanks(@TempDir Path dir) throws Exception {
            Files.writeString(dir.resolve("cv.log"), "log");
            Files.writeString(dir.resolve("cv.aux"), "aux");
            Files.writeString(dir.resolve("cv.pdf"), "keep");

            int failures = CVGenerator.cleanupIntermediateFiles(dir, "cv", List.of(".log", " .aux", "", " "));

            assertThat(failures).isZero();
            assertThat(dir.resolve("cv.log")).doesNotExist();
            assertThat(dir.resolve("cv.aux")).doesNotExist();
            assertThat(dir.resolve("cv.pdf")).exists();
        }

        @Test
        void reportsFilesThatCannotBeDeleted(@TempDir Path dir) throws Exception {
            // A non-empty directory where a file is expected cannot be deleted
            Path blocker = Files.createDirectory(dir.resolve("cv.aux"));
            Files.writeString(blocker.resolve("inner"), "x");
            Files.writeString(dir.resolve("cv.log"), "log");

            int failures = CVGenerator.cleanupIntermediateFiles(dir, "cv", List.of(".aux", ".log"));

            assertThat(failures).isEqualTo(1);
            assertThat(dir.resolve("cv.log")).doesNotExist();
        }

        @Test
        void nullExtensionListIsHarmless(@TempDir Path dir) {
            assertThat(CVGenerator.cleanupIntermediateFiles(dir, "cv", null)).isZero();
        }
    }

    @Nested
    @EnabledOnOs({OS.LINUX, OS.MAC})
    class ProcessRunner {

        private final ProcessCommandRunner runner = new ProcessCommandRunner(5);

        @Test
        void killsProcessThatOverrunsTheTimeout(@TempDir Path dir) throws Exception {
            long start = System.nanoTime();

            CommandResult result = runner.run(
                    List.of("sh", "-c", "echo started; sleep 30; echo never"),
                    dir, Duration.ofMillis(500), null);

            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            assertThat(result.timedOut()).isTrue();
            assertThat(result.exitCode()).isEqualTo(-1);
            assertThat(result.outputTail()).containsExactly("started");
            assertThat(elapsedMillis).isLessThan(10_000);
        }

        @Test
        void drainsLargeOutputAndKeepsOnlyTheTail(@TempDir Path dir) throws Exception {
            List<String> seen = new ArrayList<>();
            // About 300 KB, far more than an OS pipe buffer, plus a line on stderr
            String script = "i=0; while [ $i -lt 30000 ]; do echo line$i; i=$((i+1)); done; echo err-line 1>&2";

            CommandResult result = runner.run(List.of("sh", "-c", script), dir, Duration.ofSeconds(30), seen::add);

            assertThat(result.timedOut()).isFalse();
            assertThat(result.exitCode()).isZero();
            assertThat(seen).hasSize(30001);
            assertThat(result.outputTail())
                    .containsExactly("line29996", "line29997", "line29998", "line29999", "err-line");
        }

        @Test
        void passesExitCodeAndWorkingDirectory(@TempDir Path dir) throws Exception {
            Files.writeString(dir.resolve("marker.txt"), "here");

            CommandResult result = runner.run(
                    List.of("sh", "-c", "cat marker.txt; exit 3"), dir, Duration.ofSeconds(10), null);

            assertThat(result.timedOut()).isFalse();
            assertThat(result.exitCode()).isEqualTo(3);
            assertThat(result.outputTail()).containsExactly("here");
        }
    }

    @Nested
    @EnabledOnOs({OS.LINUX, OS.MAC})
    class CompileWithFakeCompiler {

        /** A "compiler" that runs a shell script; the .tex file name arrives as $1. */
        private LatexCompiler fakeCompiler(String script) {
            return new LatexCompiler("sh", List.of("-c", script, "fakelatex"), Duration.ofSeconds(10), 1,
                    new ProcessCommandRunner());
        }

        @Test
        void renamesProducedPdfToRequestedName(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "\\documentclass{article}");

            Path result = fakeCompiler("echo 'fake [1]'; echo pdf > \"${1%.tex}.pdf\"")
                    .compile(dir, tex, "final_cv.pdf");

            assertThat(result).isEqualTo(dir.resolve("final_cv.pdf"));
            assertThat(result).exists();
            assertThat(dir.resolve("cv.pdf")).doesNotExist();
        }

        @Test
        void keepsPdfInPlaceWhenNamesMatch(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "x");

            Path result = fakeCompiler("echo pdf > \"${1%.tex}.pdf\"").compile(dir, tex, "cv.pdf");

            assertThat(result).exists();
        }

        @Test
        void failureThrowsWithLogTail(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "x");

            assertThatThrownBy(() -> fakeCompiler("echo '! Undefined control sequence.'; exit 1")
                    .compile(dir, tex, "cv.pdf"))
                    .isInstanceOf(LatexCompilationException.class)
                    .hasMessageContaining("exit code 1")
                    .satisfies(e -> assertThat(((LatexCompilationException) e).getLogTail())
                            .contains("! Undefined control sequence."));
        }

        @Test
        void stalePdfFromEarlierRunIsNotMistakenForOutput(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "x");
            Files.writeString(dir.resolve("cv.pdf"), "old run");

            assertThatThrownBy(() -> fakeCompiler("exit 1").compile(dir, tex, "out.pdf"))
                    .isInstanceOf(LatexCompilationException.class);
            assertThat(dir.resolve("out.pdf")).doesNotExist();
        }

        @Test
        void nonZeroExitWithPdfIsAccepted(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "x");

            Path result = fakeCompiler("echo pdf > \"${1%.tex}.pdf\"; exit 1").compile(dir, tex, "out.pdf");

            assertThat(result).exists();
        }

        @Test
        void zeroExitWithoutPdfFails(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "x");

            assertThatThrownBy(() -> fakeCompiler("true").compile(dir, tex, "out.pdf"))
                    .isInstanceOf(LatexCompilationException.class)
                    .hasMessageContaining("not generated");
        }

        @Test
        void hangingCompilerIsStopped(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "x");
            LatexCompiler compiler = new LatexCompiler("sh", List.of("-c", "sleep 30", "fakelatex"),
                    Duration.ofMillis(300), 1, new ProcessCommandRunner());

            assertThatThrownBy(() -> compiler.compile(dir, tex, "out.pdf"))
                    .isInstanceOf(LatexCompilationException.class)
                    .hasMessageContaining("timed out");
        }
    }

    @Nested
    class CompileWithFakeRunner {

        @Test
        void buildCommandTrimsArgsAndSkipsBlanks() {
            LatexCompiler compiler = new LatexCompiler("pdflatex", List.of(" -interaction=nonstopmode", " ", ""),
                    Duration.ofSeconds(1), 5, (cmd, dir, timeout, listener) -> null);

            assertThat(compiler.buildCommand("cv.tex"))
                    .containsExactly("pdflatex", "-interaction=nonstopmode", "cv.tex");
        }

        @Test
        void timedOutResultThrowsEvenIfPdfExists(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "x");
            LatexCompiler compiler = new LatexCompiler("pdflatex", List.of(), Duration.ofSeconds(1), 5,
                    (cmd, workDir, timeout, listener) -> {
                        Files.writeString(workDir.resolve("cv.pdf"), "partial");
                        return new CommandResult(-1, true, List.of("still running"));
                    });

            assertThatThrownBy(() -> compiler.compile(dir, tex, "out.pdf"))
                    .isInstanceOf(LatexCompilationException.class)
                    .hasMessageContaining("timed out");
        }

        @Test
        void zeroProgressIntervalDoesNotDivideByZero(@TempDir Path dir) throws Exception {
            Path tex = Files.writeString(dir.resolve("cv.tex"), "x");
            LatexCompiler compiler = new LatexCompiler("pdflatex", List.of(), Duration.ofSeconds(1), 0,
                    (cmd, workDir, timeout, listener) -> {
                        listener.accept("[1] [2]");
                        Files.writeString(workDir.resolve("cv.pdf"), "pdf");
                        return new CommandResult(0, false, List.of());
                    });

            assertThat(compiler.compile(dir, tex, "out.pdf")).exists();
        }
    }
}
