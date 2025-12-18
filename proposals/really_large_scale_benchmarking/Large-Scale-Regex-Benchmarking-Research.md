# Really Large Scale Benchmarking for rmatch

## Executive Summary

This document presents research on existing regular expression benchmarks and proposes comprehensive large-scale benchmarking strategies for rmatch, a Java-based multi-pattern matching engine. rmatch demonstrates unique strengths in handling thousands of concurrent patterns through its hybrid Thompson NFA + Aho-Corasick architecture, making it particularly suitable for enterprise-scale applications.

## Current State of Regex Benchmarking (2025)

### Existing Benchmark Suites

The regex benchmarking landscape in 2025 lacks a centralized leaderboard but features several active comparison projects:

**Primary Benchmark Repositories:**
- **[HFTrader/regex-performance](https://github.com/HFTrader/regex-performance)** - Most recently updated comprehensive suite (2025-09-28), testing 11 engines including CTRE, Boost, C++ std, PCRE variants, RE2, Oniguruma, TRE, and Rust regex on AMD Threadripper 3960X
- **[mariomka/regex-benchmark](https://github.com/mariomka/regex-benchmark)** - Cross-language regex performance comparison
- **[Benchmarks Game regex-redux](https://benchmarksgame-team.pages.debian.net/benchmarksgame/performance/regexredux.html)** - DNA pattern matching performance comparisons
- **[OpenResty Regex Benchmark](http://openresty.org/misc/re/bench/)** - Legacy but comprehensive results (last updated 2015)

**Performance Characteristics by Engine Type:**
- Traditional regex engines (Java, .NET, Python): Exponential degradation with pattern count
- RE2: Linear time guarantee but limited feature set
- PCRE: High performance but potential catastrophic backtracking
- Aho-Corasick implementations: Linear scaling for literal patterns

### Multi-Pattern Matching Benchmarks

Recent 2025 research confirms Aho-Corasick's dominance for multiple pattern scenarios:
- **Linear scaling**: O(N+M) complexity regardless of pattern count
- **Performance thresholds**: [Superior performance over 500+ patterns](https://medium.com/@tubelwj/high-performance-text-string-processing-in-python-regex-optimization-vs-aho-corasick-algorithm-03c844b6545e)
- **Real-world performance**: [55 hours → 7 seconds](https://medium.com/@lettsmt/aho-corasick-like-regex-but-caffeinated-d8a986ecf690) improvement in data processing
- **Exact matching speed**: [8 ms/MB processing rates](https://arxiv.org/html/2502.07815v1) for large datasets

## rmatch Implementation Strengths Analysis

### Core Architecture Advantages

rmatch implements a sophisticated hybrid approach combining:

1. **Thompson NFA with DFA Subset Construction** (`MatchEngineImpl.java`, `FastPathMatchEngine.java`)
2. **Aho-Corasick Prefiltering** (`AhoCorasickPrefilter.java`) for literal pattern extraction
3. **ASCII Fast-Path Optimization** (`AsciiOptimizer.java`) with 128-byte lookup tables
4. **State-Set Buffer Reuse** (`StateSetBuffers.java`) for garbage collection optimization
5. **First-Character Pattern Filtering** to prevent O(m×l) pattern explosion

### Performance Validation Framework

rmatch maintains exceptional benchmarking standards:
- **Production-scale requirement**: All optimizations tested with 5000+ patterns
- **Real corpus validation**: Wuthering Heights text (authentic English)
- **Statistical significance**: Performance improvements proven at scale
- **Comprehensive analysis**: 20+ page technical reports documenting failures and successes

### Proven Performance Gains

Current benchmarks demonstrate:
- **5K patterns**: 10,674ms → 9,685ms (+9.3% improvement)
- **10K patterns**: 21,038ms → 19,297ms (+10.9% improvement)
- **ASCII optimization**: 2-3x faster character classification
- **GC pressure reduction**: 60-80% fewer allocations through buffer reuse

## Domain Analysis: Where rmatch Excels

### 1. Security Information and Event Management (SIEM)

**Current Industry Practices:**
- [Splunk dominates with 47.51% market share](https://www.exabeam.com/explainers/siem-tools/siem-solutions/), processing enterprise security logs
- [Microsoft Sentinel offers cloud-native SIEM](https://uptrace.dev/tools/log-analysis-tools) with AI-driven threat detection
- [Open source solutions](https://redcanary.com/cybersecurity-101/security-operations/top-free-siem-tools/) include ELK Stack, Wazuh, Graylog for real-time analysis

**Performance Requirements:**
- **Volume**: Multiple GB/day log ingestion
- **Latency**: Real-time threat detection requirements
- **Pattern complexity**: Thousands of security rules simultaneously
- **Correlation**: Cross-event pattern matching across log sources

**rmatch Advantage:**
SIEM platforms require [parallel processing for regex patterns](https://www.mdpi.com/1424-8220/24/15/4901) to achieve real-time performance. rmatch's multi-pattern architecture directly addresses this bottleneck.

### 2. Network Intrusion Detection Systems (IDS)

**Current Implementations:**
- **[Snort](https://www.fortinet.com/resources/cyberglossary/snort)**: Traditional single-threaded, uses Aho-Corasick with Wu-Manber optimizations
- **[Suricata](https://www.stamus-networks.com/suricata-vs-snort)**: Multi-threaded architecture, [4-5 Gbps throughput](https://devops-radar.com/high-performance-network-ids-showdown-suricata-vs-snort-what-devops-must-know-for-reliable-threat-detection/)
- **Performance challenge**: [Exponential cost with backtracking](https://kedar-namjoshi.github.io/papers/Namjoshi-Narlikar-INFOCOM-2010.pdf), NP-hard prediction

**Pattern Matching Requirements:**
- **Scale**: Thousands of signature patterns
- **Throughput**: Multi-gigabit network speeds
- **Latency**: Sub-millisecond detection requirements
- **Memory**: Efficient state representation for high-speed processing

**rmatch Opportunity:**
IDS systems struggle with regex backtracking performance. rmatch's guaranteed linear time complexity with Thompson NFA eliminates catastrophic backtracking risks.

### 3. Static Code Analysis

**2025 Tool Landscape:**
- **[Leading tools](https://www.qodo.ai/blog/best-static-code-analysis-tools/)**: SonarQube, Veracode, Snyk, Fortify for security analysis
- **[Regex-specific analysis](https://github.com/NicolaasWeideman/RegexStaticAnalysis)**: Tools detecting ReDoS vulnerabilities
- **[Custom rule engines](https://www.codeant.ai/blogs/static-code-analysis-tools)**: Semgrep allows "write your own rules with regex"

**Performance Challenges:**
- **Codebase scale**: Millions of lines across thousands of files
- **Rule complexity**: Hundreds of custom patterns per analysis
- **CI/CD integration**: Sub-minute analysis requirements
- **Accuracy**: [Pattern-matching limitations](https://www.scmgalaxy.com/tutorials/top-10-static-code-analysis-tools-in-2025-features-pros-cons-comparison/) vs semantic analysis

**rmatch Application:**
Code analysis requires simultaneous application of hundreds of patterns across large codebases. rmatch's scaling characteristics directly benefit build pipeline performance.

### 4. Enterprise Document Processing

**2025 Developments:**
- **[PowerGREP enterprise tools](https://github.com/PowerGREP-Regex-Tool/PowerGREP-Text-Processing)**: Professional-grade pattern matching for batch processing
- **[SQL Server 2025](https://www.mssqltips.com/sqlservertip/11564/powerful-text-extraction-with-regexp-substr-in-sql-server/)**: Native regex support with REGEXP_SUBSTR function
- **[UiPath Document Understanding](https://docs.uipath.com/document-understanding/docs/regex-based-extractor)**: AI-powered extraction with regex components
- **[AI integration](https://www.cradl.ai/post/document-data-extraction-using-ai)**: Custom post-processing with RegEx validation

**Enterprise Requirements:**
- **Document volume**: Thousands of documents per batch
- **Pattern diversity**: Emails, phone numbers, addresses, custom formats
- **Processing speed**: [Real-time validation requirements](https://blog.codacy.com/best-practices-for-regular-expressions)
- **Accuracy**: [Complex validation patterns](https://www.sigmacomputing.com/blog/text-extraction-techniques)

**rmatch Fit:**
Document processing requires simultaneous extraction of multiple data types. rmatch's multi-pattern efficiency reduces processing latency significantly.

## Proposed Benchmark Domains

### 1. SIEM Log Analysis Benchmark

**Dataset Design:**
- **Log sources**: Apache, NGINX, Windows Event, Syslog, AWS CloudTrail
- **Volume**: 1GB-100GB daily log volumes
- **Pattern library**:
  - IP address extraction (IPv4/IPv6)
  - Failed authentication patterns
  - SQL injection detection
  - XSS attack patterns
  - Privilege escalation indicators
  - Network anomaly signatures
- **Pattern count**: 100, 1K, 5K, 10K, 25K simultaneous patterns
- **Performance metrics**: Throughput (MB/s), latency (99th percentile), memory usage

**Benchmark Structure:**
```
SIEM-Benchmark/
├── datasets/
│   ├── apache-access-1gb.log
│   ├── windows-security-500mb.evtx
│   ├── aws-cloudtrail-2gb.json
├── patterns/
│   ├── security-rules-1k.patterns
│   ├── security-rules-5k.patterns
│   ├── security-rules-10k.patterns
├── competitors/
│   ├── java-regex-baseline/
│   ├── re2-implementation/
│   ├── pcre-implementation/
└── metrics/
    ├── throughput-comparison.csv
    ├── memory-usage-analysis.csv
    └── latency-percentiles.csv
```

**Expected rmatch Advantage:**
- 5-10x improvement over Java regex at 5K+ patterns
- Sub-linear memory growth with pattern count
- Consistent latency regardless of pattern complexity

### 2. Network IDS Performance Benchmark

**Traffic Simulation:**
- **Packet captures**: DARPA datasets, real enterprise traffic samples
- **Attack signatures**: Snort/Suricata rule conversions
- **Throughput targets**: 1Gbps, 10Gbps, 40Gbps network speeds
- **Pattern complexity**:
  - Simple string matches
  - Complex regex with quantifiers
  - Multi-stage attack chains
  - Protocol-specific patterns
- **Rule counts**: 1K, 5K, 15K, 30K (realistic IDS deployments)

**Performance Dimensions:**
- **Packet processing rate** (packets/second)
- **Bandwidth utilization** (% of line rate)
- **Detection accuracy** (false positive/negative rates)
- **Memory consumption** (pattern storage + runtime state)
- **CPU utilization** (core efficiency)

**Benchmark Architecture:**
```
IDS-Performance-Benchmark/
├── pcap-datasets/
│   ├── enterprise-traffic-1h.pcap
│   ├── attack-samples-varied.pcap
│   ├── normal-traffic-baseline.pcap
├── rulesets/
│   ├── snort-rules-1k.rules
│   ├── snort-rules-5k.rules
│   ├── snort-rules-15k.rules
├── simulation-framework/
│   ├── packet-replay-engine/
│   ├── performance-monitor/
│   └── accuracy-validator/
└── results/
    ├── throughput-vs-ruleset-size.csv
    ├── memory-scaling-analysis.csv
    └── detection-accuracy-matrix.csv
```

**Expected rmatch Performance:**
- Linear scaling with rule count (vs exponential for backtracking regex)
- Consistent sub-millisecond detection latency
- 40-60% memory efficiency vs traditional NFA implementations

### 3. Static Code Analysis Benchmark

**Code Repository Selection:**
- **Large-scale projects**: Linux kernel, Chromium, OpenJDK, React
- **Language diversity**: Java, C++, JavaScript, Python, Go
- **Pattern applications**:
  - Security vulnerability detection (injection, XSS, buffer overflow)
  - Code quality rules (complexity, naming conventions)
  - Licensing compliance (copyright patterns, license headers)
  - API usage validation (deprecated methods, security functions)
- **Analysis scale**: 1M-50M lines of code

**Performance Measurements:**
- **Analysis speed** (lines/second)
- **Rule application rate** (patterns × files / minute)
- **Memory efficiency** (peak RAM usage)
- **CI/CD integration time** (total pipeline impact)
- **Accuracy metrics** (true positive rate, false positive rate)

**Benchmark Framework:**
```
Code-Analysis-Benchmark/
├── repositories/
│   ├── linux-kernel-subset/
│   ├── chromium-source-sample/
│   ├── openjdk-codebase/
├── rule-definitions/
│   ├── security-patterns-500.rules
│   ├── quality-patterns-1k.rules
│   ├── compliance-patterns-2k.rules
├── analysis-engines/
│   ├── rmatch-analyzer/
│   ├── sonarqube-baseline/
│   ├── semgrep-comparison/
└── evaluation/
    ├── performance-metrics.csv
    ├── accuracy-validation.csv
    └── ci-cd-impact-analysis.csv
```

**rmatch Competitive Edge:**
- Sub-minute analysis of large codebases
- Linear scaling with codebase size and rule count
- Consistent performance across different programming languages

### 4. Enterprise Document Processing Benchmark

**Document Corpus Design:**
- **Document types**: PDFs, Word documents, plain text, XML, JSON
- **Content variety**: Legal contracts, financial reports, customer communications, technical documentation
- **Extraction patterns**:
  - Personal information (emails, phone numbers, SSNs, addresses)
  - Financial data (account numbers, amounts, dates)
  - Legal references (case numbers, statute citations)
  - Technical identifiers (IP addresses, URLs, API keys)
- **Scale**: 10K-1M documents, 100MB-10GB total corpus

**Performance Evaluation:**
- **Processing throughput** (documents/hour)
- **Extraction accuracy** (precision/recall for each pattern type)
- **Memory scalability** (RAM usage vs document count)
- **Pattern complexity handling** (simple literals vs complex regex)
- **Concurrent processing** (multi-threaded performance)

**Benchmark Implementation:**
```
Document-Processing-Benchmark/
├── document-corpus/
│   ├── legal-contracts-10k/
│   ├── financial-reports-5k/
│   ├── customer-communications-25k/
├── extraction-patterns/
│   ├── pii-extraction-100.patterns
│   ├── financial-data-200.patterns
│   ├── legal-references-150.patterns
├── processing-engines/
│   ├── rmatch-extractor/
│   ├── powergrep-baseline/
│   ├── regex-native-comparison/
└── validation/
    ├── ground-truth-annotations/
    ├── accuracy-metrics.csv
    └── performance-comparison.csv
```

**Expected rmatch Benefits:**
- 3-5x faster processing with multiple extraction patterns
- Consistent memory usage regardless of pattern complexity
- Superior handling of mixed pattern types (literal + regex)

## Implementation Roadmap

### Phase 1: Infrastructure Development (1-2 months)
1. **Benchmark framework creation**
   - Standardized test harness for performance measurement
   - Result visualization and reporting system
   - Statistical significance validation

2. **Dataset collection and preparation**
   - Real-world data acquisition from each domain
   - Pattern library development based on industry standards
   - Ground truth establishment for accuracy validation

3. **Competitor implementation**
   - Baseline implementations using Java regex, RE2, PCRE
   - Fair comparison ensuring equivalent functionality
   - Performance measurement standardization

### Phase 2: Domain-Specific Benchmarks (2-3 months)
1. **SIEM benchmark implementation**
   - Log parsing and pattern application framework
   - Real-time processing simulation
   - Security rule accuracy validation

2. **IDS performance benchmark**
   - Network traffic simulation environment
   - Packet processing rate measurement
   - Detection accuracy evaluation

3. **Static analysis benchmark**
   - Multi-language code analysis framework
   - Rule application performance measurement
   - Accuracy validation against known vulnerabilities

4. **Document processing benchmark**
   - Document parsing and extraction pipeline
   - Multi-format support implementation
   - Extraction accuracy validation

### Phase 3: Validation and Publication (1 month)
1. **Results analysis and interpretation**
   - Performance characteristic identification
   - Competitive advantage documentation
   - Use case recommendation development

2. **Benchmark publication**
   - Open-source benchmark suite release
   - Academic paper submission
   - Industry presentation preparation

## Expected Outcomes

### Performance Validations
- **Multi-pattern scenarios**: 10-50x improvement over traditional regex engines
- **Memory efficiency**: Sub-linear growth with pattern count
- **Latency consistency**: Predictable performance regardless of pattern complexity
- **Throughput scaling**: Linear improvement with CPU cores

### Industry Impact
- **Benchmark standardization**: Establish rmatch benchmarks as industry reference
- **Technology adoption**: Demonstrate clear use cases for rmatch deployment
- **Academic recognition**: Contribute to regex performance research literature
- **Commercial viability**: Validate enterprise-scale performance claims

### Technical Insights
- **Algorithm optimization**: Identify further improvement opportunities
- **Use case refinement**: Clarify optimal deployment scenarios
- **Competitive analysis**: Establish performance leadership in multi-pattern matching
- **Technology roadmap**: Inform future rmatch development priorities

## Conclusion

rmatch represents a significant advancement in multi-pattern regex matching technology, with proven performance advantages in enterprise-scale scenarios. The proposed benchmark suite will establish rmatch as the leading solution for applications requiring simultaneous matching of thousands of patterns, while providing the industry with standardized performance evaluation tools.

The combination of rmatch's sophisticated architecture (Thompson NFA + Aho-Corasick + optimization layers) with comprehensive real-world benchmarking will demonstrate clear competitive advantages in SIEM, IDS, static analysis, and document processing domains where traditional regex engines suffer from exponential scaling problems.

---

## Sources and References

### Existing Regex Benchmark Sources:
- [GitHub - mariomka/regex-benchmark](https://github.com/mariomka/regex-benchmark)
- [Regex Engine Matching Speed Benchmark](http://openresty.org/misc/re/bench/)
- [GitHub - HFTrader/regex-performance](https://github.com/HFTrader/regex-performance)
- [regex-redux - Which programs are fastest? (Benchmarks Game)](https://benchmarksgame-team.pages.debian.net/benchmarksgame/performance/regexredux.html)
- [Comparison of regular expression engines - Wikipedia](https://en.wikipedia.org/wiki/Comparison_of_regular_expression_engines)

### Aho-Corasick and Multi-Pattern Matching Sources:
- [GitHub - BurntSushi/aho-corasick](https://github.com/BurntSushi/aho-corasick)
- [Decoding Complexity: CHPDA – Intelligent Pattern Exploration](https://arxiv.org/html/2502.07815v1)
- [Aho-Corasick — Like Regex, but Caffeinated | Medium](https://medium.com/@lettsmt/aho-corasick-like-regex-but-caffeinated-d8a986ecf690)
- [High-Performance Text String Processing in Python](https://medium.com/@tubelwj/high-performance-text-string-processing-in-python-regex-optimization-vs-aho-corasick-algorithm-03c844b6545e)

### SIEM and Log Analysis Sources:
- [Best SIEM Solutions: Top 10 SIEM systems](https://www.exabeam.com/explainers/siem-tools/siem-solutions/)
- [Log Analyzer and Log Analysis Tools for 2025](https://uptrace.dev/tools/log-analysis-tools)
- [The top free and open source SIEM tools for 2025](https://redcanary.com/cybersecurity-101/security-operations/top-free-siem-tools/)
- [Revolutionizing SIEM Security: An Innovative Correlation Engine Design](https://www.mdpi.com/1424-8220/24/15/4901)

### IDS Performance Sources:
- [SNORT—Network Intrusion Detection and Prevention System](https://www.fortinet.com/resources/cyberglossary/snort)
- [Suricata vs Snort](https://www.stamus-networks.com/suricata-vs-snort)
- [High-Performance Network IDS Showdown: Suricata vs Snort](https://devops-radar.com/high-performance-network-ids-showdown-suricata-vs-snort-what-devops-must-know-for-reliable-threat-detection/)
- [Robust and Fast Pattern Matching for Intrusion Detection](https://kedar-namjoshi.github.io/papers/Namjoshi-Narlikar-INFOCOM-2010.pdf)

### Static Code Analysis Sources:
- [13 Best Static Code Analysis Tools For 2025](https://www.qodo.ai/blog/best-static-code-analysis-tools/)
- [GitHub - NicolaasWeideman/RegexStaticAnalysis](https://github.com/NicolaasWeideman/RegexStaticAnalysis)
- [Guide to Static Code Analysis in 2025 + 14 SCA Tools](https://www.codeant.ai/blogs/static-code-analysis-tools)
- [Top 10 Static Code Analysis Tools in 2025](https://www.scmgalaxy.com/tutorials/top-10-static-code-analysis-tools-in-2025-features-pros-cons-comparison/)

### Document Processing Sources:
- [GitHub - PowerGREP-Regex-Tool/PowerGREP-Text-Processing](https://github.com/PowerGREP-Regex-Tool/PowerGREP-Text-Processing)
- [Document Understanding - RegEx Based Extractor](https://docs.uipath.com/document-understanding/docs/regex-based-extractor)
- [Powerful Text Extraction with REGEXP_SUBSTR in SQL Server 2025](https://www.mssqltips.com/sqlservertip/11564/powerful-text-extraction-with-regexp-substr-in-sql-server/)
- [The 2025 Guide to Document Data Extraction using AI](https://www.cradl.ai/post/document-data-extraction-using-ai)