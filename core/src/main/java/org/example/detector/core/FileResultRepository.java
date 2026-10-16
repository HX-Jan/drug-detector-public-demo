package org.example.detector.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Desktop adapter: one immutable record per UUID with atomic file replacement. */
public final class FileResultRepository implements ResultRepository {
  private final Path directory;

  public FileResultRepository(Path directory) throws IOException {
    this.directory = directory;
    Files.createDirectories(directory);
  }

  public synchronized void save(DetectionResult result) throws IOException {
    UUID.fromString(result.id);
    Path target = directory.resolve(result.id + ".dtp");
    if (Files.exists(target)) throw new IOException("记录已存在，拒绝覆盖");
    Path temp = Files.createTempFile(directory, "pending-", ".tmp");
    try {
      Files.write(temp, ResultCodec.encode(result));
      try {
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temp, target);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public synchronized List<DetectionResult> all() throws IOException {
    List<DetectionResult> results = new ArrayList<>();
    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.dtp")) {
      for (Path p : files) {
        if (Files.size(p) > 200000) throw new IOException("记录过大：" + p.getFileName());
        results.add(ResultCodec.decode(Files.readAllBytes(p)));
      }
    }
    results.sort((a, b) -> Long.compare(b.epochMillis, a.epochMillis));
    return results;
  }
}
