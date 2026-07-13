package engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileSystemIO {
    private static final String PLAYGROUND_DIR = "bit-playground";

    // Initializes root directory and creates startup files
    public void initalizePlayground() {
        Path path = Paths.get(PLAYGROUND_DIR);
        // If path doesn't exists, creates root directory and default files
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
                System.out.println("Created storage directory: " + path.toAbsolutePath());

                generateDefaultFile("welcome.txt", "Welcome to bit-vcs!\nThis is a real-world file sandbox.");
                generateDefaultFile("todo.md", "# Project Tasks\n- [ ] Master Graphs\n- [ ] Finish LRU Cache");

                System.out.println("Successfully initialized workspace files inside './bit-playground/'.");
            } catch (IOException e) {
                System.out.println(
                        "Fatal Error: Failed to initialize file playground directory structure: " + e.getMessage());
            }
        } 
        // Else continue if path exists
        else {
            System.out
                    .println("Notification: Existing 'bit-playground' directory detected. Re-binding tracking hooks.");
        }
    }

    // Opens editor for a file
    public void openNativeEditor(String fileName) {
        Path filePath = Paths.get(PLAYGROUND_DIR, fileName);

        if (!Files.exists(filePath)) {
            System.out.println("Error: File '" + fileName + "' does not exist in the workspace.");
            return;
        }

        System.out.println("Launching external text editor environment process thread...");
        System.out.println("Modifying target: " + filePath.toAbsolutePath());
        System.out.println("--> Please save your changes and CLOSE the editor to return to the bit console loop. <--");

        try {
            // Spawns an editor process depending on the OS
            ProcessBuilder processBuilder;
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("notepad.exe", filePath.toString());
            } else if (os.contains("mac")) {
                processBuilder = new ProcessBuilder("open", "-e", filePath.toString());
            } else {
                processBuilder = new ProcessBuilder("nano", filePath.toString());
            }
            
            // Inherits the IO of the process, starts it, and pauses its execution till process is exited from
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            System.out.println("Process context returned. Text editor session exited with status code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            System.out.println("Error running external editor interface process engine: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    // Shutdown hook that destroys all files and directories under root directory at exit
    public void purgePlayground() {
        Path path = Paths.get(PLAYGROUND_DIR);
        if (!Files.exists(path)) return;

        try {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            }
            System.out.println("Workspace clean up complete. Physical 'bit-playground' directory removed.");
        } catch (IOException e) {
            System.out.println("Warning: Automated cleanup failed to completely purge directory: " + e.getMessage());
        }
    }

    // Generates a file under the root directory
    private static void generateDefaultFile(String fileName, String content) throws IOException {
        Path targetPath = Paths.get(PLAYGROUND_DIR, fileName);
        Files.writeString(targetPath, content);
    }
}
