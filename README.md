# Source Compare

Spring Boot web application for comparing Java sources and classes.

## Tech Stack

- **Maven** for build management
- **Spring Boot** with **Thymeleaf** for backend and views
- **FernFlower** decompiler for `.class` files
- **google-java-format** for Java formatting
- **jsoup** for HTML normalization
- **java-diff-utils** for line-level diff
- **ASM** for class structure analysis
- **diff2html** for frontend diff rendering

# Web App: Java/Webapp Source Comparator

## 1. Input

- Always 2 uploaded ZIP files.
- Each ZIP may contain:

+ .class files (compiled Java)
+ .java files (source code)
+ .jsp, .html, .js, .css, and other web resources.

## 2. Comparison Modes

## Class vs Source Mode (primary):

- Left side: .class files → decompile into Java.
- Right side: .java source files.
- Both sides → format/normalize to reduce false differences.
- Diff the results.

## Class vs Class Mode (secondary option):

- Compare .class files from both ZIPs.
- Parse with ASM to extract signatures/methods/fields → diff the structure.

## Source vs Source Mode:

- Compare .java source files from both ZIPs.
- Format and normalize each side using google-java-format.
- Diff the results.

## 3. Normalization (before diffing)

### Java (.class / .java):

- Decompile .class → Java source (FernFlower).
- Format with one standard formatter (google-java-format or Eclipse JDT).
- Normalize line endings (\n) and trim trailing spaces.

### JSP / HTML:

- Use jsoup to parse & pretty-print → consistent indentation and spacing.

### JavaScript / CSS:

- Basic normalization: consistent indentation, remove trailing whitespace.
- Other files (binary/images):
- Compare using hash only; no line diff.

## 4. Diff Engine

File-level comparison:

- Added: only in right ZIP.
- Deleted: only in left ZIP.
- Modified: exists in both but content differs after normalization.
- Renamed (optional): detect if deleted+added file are >85% similar.
  Line-level comparison:
- Use Myers algorithm (via java-diff-utils) to generate unified diff.

## 5. Output (UI)

- File Tree View: shows all files, marked as Added / Modified / Deleted.
- Side-by-Side Diff View: like GitHub:
    + Left: first ZIP (e.g., Decompiled Classes)
    + Right: second ZIP (e.g., Sources)
    + Highlights exact differences.
    + Filters: by file type (.java, .jsp, .html, .js) and change type (A/M/D).
    + Search bar: filter files by name/path.
    + Export: full HTML report (with embedded diffs) + JSON summary.

## 6. Workflow

- User uploads two ZIPs.
- System unpacks both → normalized directory structures.
- For each file:
    + If .class → decompile → format.
    + If .java → read → format.
    + If .jsp/.html → jsoup normalize.
    + If .js/.css → basic normalize.
    + Else → compare hashes only.
- Compare file lists → detect Added/Deleted/Modified.
- For Modified files → run line-level diff.
- Store results as JSON + unified diff text.
- UI loads summary tree → click → show diff via diff2html.

## 7. Scale & Limits

- Handles up to 10k files.
- Uses streaming unzip (don’t load everything in memory at once).
- Decompilation submits each `.class` entry directly to a completion service so that
  only a handful of class byte arrays are resident at any moment, even for archives
  with hundreds of megabytes of bytecode.
- Parallelizes decompile/format with thread pool.
- Lazy-load diffs in UI (diff only rendered when user clicks a file).

## 10. Testing & Validation

- Unit tests exercise the streaming decompilation pipeline, including a large archive
  regression that tracks heap deltas to guard against reintroducing the previous
  buffering behaviour.

## 8. Deployment

- Backend: Spring Boot (Java 11+).
- Frontend: Thymeleaf templates, diff rendering via diff2html.
- Zip Handling: Java java.util.zip or Zip4j.
- Data Storage: temp directories per job, auto-cleanup after TTL.
- Security: internal tool, no login; file size limit ~1.5 GB per ZIP; safe handling (no execution).

## 9. Extra Features

- Ignore rules (like .gitignore) → skip irrelevant files (target/, .map, .min.js).
- Export HTML/JSON report for offline review.
- CLI companion mode (optional) for Jenkins integration.
- Multi-layer archive support (unzip WAR/JAR inside ZIPs).

# Final Tech Stack

- Spring Boot + Thymeleaf → web app backend + views.
- FernFlower → decompiler for .class.
- google-java-format (or Eclipse JDT) → formatter for Java.
- jsoup → HTML/JSP normalization.
- java-diff-utils → line-level diff.
- ASM → class structure analysis (Class vs Class mode).
- diff2html → frontend diff rendering.
