package cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import cli.BitCommand;
import engine.FileSystemIO;
import engine.StorageEngine;

public class EditCommand implements BitCommand {
    @Override
    public void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO) {
        if (tokens.size() < 3) {
            System.out.println("Error: Specify a file name. Example: bit edit note.txt");
            return;
        }

        boolean forceCreate = tokens.get(2).equals("-n");
        Path playgroundPath = Paths.get("bit-playground");

        if (forceCreate && tokens.size() < 4) System.out.println("Error: Missing filename after '-n'. Example: bit edit -n src/app.js");

        // Force all OS paths to standard Merkle forward-slashes
        String targetPathString = (forceCreate ? tokens.get(3) : tokens.get(2)).replace("\\", "/");
        
        Path targetPath = playgroundPath.resolve(targetPathString);

        if (!Files.exists(playgroundPath)) {
            System.out.println("No root directory initialized. Please run 'bit init' first.");
            return;
        }

        // If file exists and no forced create option mentioned, open native editor
        List<String> matches = storageEngine.getTrieEngine().searchPrefix(targetPathString);
        if (!matches.isEmpty() && !forceCreate) {
                fileSystemIO.openNativeEditor(matches.get(0));
            return;
        }

        // If file doesn't exist and no forced create option mentioned, ask if user wants to create a new file
        if (!forceCreate) {
            System.out.print("Target path '" + targetPathString + "' is untracked. Create new entry? (y/n): ");
            try {
                char choice = (char) System.in.read();
                while (System.in.available() > 0) System.in.read(); 
                if (choice != 'y' && choice != 'Y') {
                    System.out.println("Aborted.");
                    return;
                }
            } catch (IOException e) { return; }
        }

        // Create new file, and open editor
        try {
            // If no actual file in path, just directories -> create directories
            if (!targetPathString.contains(".")) {
                Files.createDirectories(targetPath);
                System.out.println("Created directory: " + targetPathString);
            } else {
                // Create non-existent parent directories and the new file
                if (targetPath.getParent() != null) Files.createDirectories(targetPath.getParent());
                Files.writeString(targetPath, "");
                
                // Insert file name in trie and open native editor
                storageEngine.getTrieEngine().insert(targetPathString);
                fileSystemIO.openNativeEditor(targetPathString);
            }
        } catch (IOException e) {
            System.out.println("FileSystem Error: " + e.getMessage());
        }
    }
}
