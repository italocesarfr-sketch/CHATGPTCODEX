#!/usr/bin/env python3
import json,re,sys
from pathlib import Path
text=Path(sys.argv[1]).read_text()
m=re.search(r"OK \((\d+) tests?\)",text)
assert m and int(m[1])>0 and not any(x in text for x in ["FAILURES", "INSTRUMENTATION_FAILED", "Process crashed", "shortMsg=", "INSTRUMENTATION_STATUS_CODE: -3", "INSTRUMENTATION_STATUS_CODE: -4"]),text
print(json.dumps(dict(passed=int(m[1]),failed=0,ignored=0)))
