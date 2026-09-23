import os, re

pattern = re.compile(r'Text\s*\(\s*(?:text\s*=\s*)?"([a-zA-Z][a-zA-Z0-9\s,\.\?\!\:\'\-]{2,})"')

hardcoded = []

for root, dirs, files in os.walk("app/src/main/java/com/example"):
    for file in files:
        if file.endswith(".kt") and file != "Loc.kt":
            path = os.path.join(root, file)
            with open(path) as f:
                for line_idx, line in enumerate(f):
                    for match in pattern.finditer(line):
                        text = match.group(1).strip()
                        if text in ["TAG", "DEBUG", "OK", "URI", "UTF-8"]:
                            continue
                        if "%" in text:
                            continue
                        hardcoded.append((file, line_idx + 1, text, line.strip()))

print(f"Total potential hardcoded strings in Text(): {len(hardcoded)}")
for f, l, t, raw in hardcoded:
    print(f"[{f}:{l}] \"{t}\" -> {raw}")
