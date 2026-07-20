package cli.commands;

import java.util.List;

import cli.BitCommand;
import engine.FileSystemIO;
import engine.StorageEngine;

public class InitCommand implements BitCommand {
    public void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO) {
        fileSystemIO.initalizePlayground();
    }
}
