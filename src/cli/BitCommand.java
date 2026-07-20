package cli;

import java.util.List;

import engine.FileSystemIO;
import engine.StorageEngine;

public interface BitCommand {
    void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO);    
} 