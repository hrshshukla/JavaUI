import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;


public class JavaRunManager {

    private final Consumer<String> appendOutput;
    private final Runnable onFinish;            

    private volatile Process currentProcess = null;
    private SwingWorker<Void, String> runWorker = null;

    public JavaRunManager(Consumer<String> appendOutput, Runnable onFinish) {
        this.appendOutput = appendOutput;
        this.onFinish = onFinish;
    }

    public synchronized void startRun(String javaFilePath) {
        stopRun();

        runWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                File javaFile = new File(javaFilePath);
                String parentDir = javaFile.getParentFile().getAbsolutePath();

                publish("Compiling: " + javaFile.getName() + "\n");
                ProcessBuilder compilePb = new ProcessBuilder("javac", javaFile.getName());
                compilePb.directory(new File(parentDir));
                compilePb.redirectErrorStream(true);
                Process compileProcess = compilePb.start();

                currentProcess = compileProcess;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        publish(line + "\n");
                    }
                }
                int compileExit = compileProcess.waitFor();
                currentProcess = null;

                if (compileExit != 0) {
                    publish("Compilation failed (exit " + compileExit + ")\n");
                    return null;
                }
                publish("Compilation succeeded.\n");

                String className = javaFile.getName().replaceAll("\\.java$", "");
                String packageName = null;
                try (Scanner sc = new Scanner(javaFile)) {
                    while (sc.hasNextLine()) {
                        String l = sc.nextLine().trim();
                        if (l.startsWith("package ")) {
                            packageName = l.substring(8).replace(";", "").trim();
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                String fqName = (packageName != null && !packageName.isEmpty()) ? (packageName + "." + className) : className;

                publish("Running: java -cp " + parentDir + " " + fqName + "\n");
                ProcessBuilder runPb = new ProcessBuilder("java", "-cp", parentDir, fqName);
                runPb.directory(new File(parentDir));
                runPb.redirectErrorStream(true);
                Process runProcess = runPb.start();

                currentProcess = runProcess;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        publish(line + "\n");
                    }
                }
                int runExit = runProcess.waitFor();
                currentProcess = null;
                publish("Process exited with code " + runExit + "\n");
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String s : chunks) {
                    safeAppend(s);
                }
            }

            @Override
            protected void done() {
                try {
                    if (onFinish != null) onFinish.run();
                } catch (CancellationException ignored) {
                } catch (Exception ex) {
                    safeAppend("Error in onFinish callback: " + ex.getMessage() + "\n");
                } finally {
                    currentProcess = null;
                    runWorker = null;
                }
            }
        };

        runWorker.execute();
    }

    public synchronized void stopRun() {
        if (runWorker != null) {
            try {
                runWorker.cancel(true);
            } catch (Exception ignored) {}
            runWorker = null;
        }
        if (currentProcess != null) {
            try {
                currentProcess.destroyForcibly();
            } catch (Exception ignored) {
            } finally {
                currentProcess = null;
            }
        }
        safeAppend("\nProcess stopped by user.\n");
    }

    public void safeAppend(String s) {
        if (appendOutput == null) return;
        SwingUtilities.invokeLater(() -> appendOutput.accept(s));
    }

    public boolean isRunning() {
        return currentProcess != null || (runWorker != null && !runWorker.isDone());
    }
}
