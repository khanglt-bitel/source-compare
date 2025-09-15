package com.example.sourcecompare;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import java.io.File;
import java.util.Map;

public class ConsoleDecompilerPublic extends ConsoleDecompiler {
    public ConsoleDecompilerPublic(File destination, Map<String, Object> options, IFernflowerLogger logger) {
        super(destination, options, logger);
    }

    public ConsoleDecompilerPublic(File destination, Map<String, Object> options, IFernflowerLogger logger, SaveType saveType) {
        super(destination, options, logger, saveType);
    }
}
