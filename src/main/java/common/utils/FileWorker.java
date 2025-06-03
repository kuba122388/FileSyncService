package common.utils;

import common.model.FileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class FileWorker {
    private final Path basePath;
    private final boolean addDirectories;

    public FileWorker(Path basePath, boolean addDirectories) {
        this.basePath = basePath;
        this.addDirectories = addDirectories;
    }

    public List<FileInfo> walkFolder() {
        return walkFolder(basePath);
    }

    private List<FileInfo> walkFolder(Path directory) {
        List<FileInfo> allFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.list(directory)) {
            paths.forEach(file -> {
                if (Files.isDirectory(file)) {
                    allFiles.addAll(this.walkFolder(file));
                    if (addDirectories) {
                        try {
                            FileInfo fileInfo = new FileInfo(
                                    basePath.relativize(file).toString(),
                                    Files.getLastModifiedTime(file).toMillis());
                            allFiles.add(fileInfo);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    try {
                        FileInfo fileInfo = new FileInfo(
                                basePath.relativize(file).toString(),
                                Files.getLastModifiedTime(file).toMillis());
                        allFiles.add(fileInfo);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return allFiles;
    }

    public void showFolderContent() {
        List<FileInfo> list = walkFolder();

        for (FileInfo file : list) {
            System.out.println(file.filePath());
            System.out.println(file.modificationDate());
            System.out.println("======================");
        }
    }

}
