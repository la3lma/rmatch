# Task 003: Input Corpus Diversification

## Title
Expand Test Input Corpus Beyond Wuthering Heights

## Problem
Testing primarily on a single literary text (Wuthering Heights) limits the generalizability of performance results. Different text types may reveal different performance characteristics, and the current corpus doesn't represent the diversity of real-world text processing scenarios.

Current corpus limitations:
- Single domain (19th century literature)
- English language only
- Specific text structure and vocabulary
- Limited character set usage
- No synthetic stress test inputs
- No structured data formats

## Proposal
Develop a diverse corpus collection that represents various text processing scenarios:

### Text Domain Categories

1. **Literature Corpus (Multiple Works)**
   - Classic literature: Pride and Prejudice, Alice in Wonderland, etc.
   - Modern literature: Recent public domain works
   - Poetry: Various forms and structures
   - Multiple languages: French, German, Spanish public domain texts
   - **Size**: 50+ works, ~100MB total

2. **Technical Text Corpus**
   - Source code: Java, Python, JavaScript, C++ samples
   - Configuration files: XML, JSON, YAML formats
   - Documentation: Markdown, LaTeX, HTML
   - Log files: Web server, application, system logs
   - **Size**: 1000+ files, ~500MB total

3. **Structured Data Corpus**
   - CSV files with various delimiters and escaping
   - Tab-separated data with different structures
   - Semi-structured text: email formats, chat logs
   - Mixed format documents
   - **Size**: 500+ files, ~200MB total

4. **Unicode Stress Test Corpus**
   - Multi-byte character documents (Chinese, Japanese, Arabic)
   - Emoji-heavy social media text
   - Mathematical notation and symbols
   - Right-to-left text processing
   - **Size**: 100+ files, ~50MB total

5. **Synthetic Stress Test Corpus**
   - High match density inputs (many pattern matches)
   - Low match density inputs (few or no matches)
   - Pathological inputs designed to stress regex engines
   - Very large files (GB-scale) for scalability testing
   - **Size**: Programmatically generated, up to 10GB

### Corpus Characteristics Matrix

```java
public class CorpusMetadata {
    private final TextDomain domain;
    private final CharacterSet characterSet;
    private final int averageLineLength;
    private final double matchDensity;  // expected matches per KB
    private final Set<TextFeature> features;
    private final String language;
    private final long sizeBytes;
}

public enum TextDomain {
    LITERATURE,
    SOURCE_CODE,
    LOG_FILES,
    STRUCTURED_DATA,
    SOCIAL_MEDIA,
    TECHNICAL_DOCS,
    SYNTHETIC
}

public enum TextFeature {
    MULTI_BYTE_CHARS,
    HIGH_PUNCTUATION,
    NUMERIC_HEAVY,
    MIXED_CASE,
    LONG_LINES,
    SHORT_LINES,
    REPEATED_PATTERNS,
    SPARSE_CONTENT
}
```

### Corpus Management System

```java
public interface CorpusManager {
    List<TextCorpus> getCorpusByDomain(TextDomain domain);
    TextCorpus loadCorpus(String corpusId);
    List<TextCorpus> selectCorporaForTest(TestRequirements requirements);
    CorpusStatistics analyzeCorpus(TextCorpus corpus);
}

public class TextCorpus {
    private final String id;
    private final CorpusMetadata metadata;
    private final List<InputFile> files;
    private final CorpusStatistics statistics;
    
    public Stream<String> getTextStream() {
        // Efficient streaming of text content
    }
    
    public String getRandomSample(int sizeBytes) {
        // Get random sample for quick testing
    }
}
```

### Synthetic Input Generation

1. **Match Density Generators**
   ```java
   public class MatchDensityGenerator {
       public String generateHighDensityInput(List<String> patterns, int targetSize);
       public String generateLowDensityInput(List<String> patterns, int targetSize);
       public String generateNoMatchInput(List<String> patterns, int targetSize);
   }
   ```

2. **Stress Test Generators**
   ```java
   public class StressTestGenerator {
       public String generateRepeatedPatternInput(String pattern, int repetitions);
       public String generatePathologicalInput(String problematicPattern);
       public String generateLargeScaleInput(int targetSizeGB);
   }
   ```

3. **Unicode Complexity Generators**
   ```java
   public class UnicodeGenerator {
       public String generateMultiByteText(int targetSize);
       public String generateEmojiHeavyText(int targetSize);
       public String generateMixedScriptText(int targetSize);
   }
   ```

### Implementation Plan

1. **Corpus Collection (Weeks 1-2)**
   - Identify and download public domain texts
   - Collect open-source code repositories
   - Gather publicly available log file samples
   - Create structured data samples

2. **Corpus Processing (Week 3)**
   - Implement text cleaning and normalization
   - Create corpus metadata extraction
   - Build corpus indexing system
   - Add file format detection

3. **Synthetic Generation (Weeks 4-5)**
   - Implement match density generators
   - Create stress test input generation
   - Build Unicode complexity generators
   - Add large-scale input generation

4. **Management System (Week 6)**
   - Implement corpus selection algorithms
   - Create corpus statistics analysis
   - Build corpus validation system
   - Add corpus update mechanisms

5. **Integration (Week 7)**
   - Integrate with test framework
   - Add corpus selection to test orchestration
   - Implement efficient streaming
   - Create corpus management tools

## Alternatives

### Alternative 1: Use Existing Public Corpora
- **Pros**: Quick setup, well-established datasets
- **Cons**: May not cover rmatch-specific needs, licensing considerations
- **Effort**: 3-4 weeks

### Alternative 2: Generate All Synthetic Corpora
- **Pros**: Controlled characteristics, unlimited variety
- **Cons**: May not reflect real-world usage patterns
- **Effort**: 6-8 weeks

### Alternative 3: Hybrid Real + Synthetic (Recommended)
- **Pros**: Realistic patterns + controlled testing
- **Cons**: More complex setup and management
- **Effort**: 7-9 weeks

### Alternative 4: Community-contributed Corpora
- **Pros**: Diverse real-world inputs, community engagement
- **Cons**: Quality control, privacy/licensing issues
- **Effort**: 8-12 weeks

## Success Criteria
- [ ] 5+ text domains represented with adequate coverage
- [ ] Corpus management system fully operational
- [ ] Synthetic generation for all stress test scenarios
- [ ] Unicode complexity coverage implemented
- [ ] Corpus selection algorithms working correctly
- [ ] Performance impact of corpus loading < 10% overhead
- [ ] Integration with existing test framework complete
- [ ] Documentation for corpus contributors

## Testing Strategy
1. **Corpus Quality Validation**
   - Verify text encoding correctness
   - Validate metadata accuracy
   - Test file format detection

2. **Performance Impact Assessment**
   - Measure corpus loading times
   - Assess memory usage for different corpus sizes
   - Validate streaming performance

3. **Coverage Testing**
   - Ensure all text domains adequately represented
   - Validate synthetic generation algorithms
   - Test corpus selection logic

## Dependencies
- Task 001: Foundation Infrastructure
- Task 002: Pattern Library Development
- File system and storage infrastructure
- Text processing utilities

## Estimated Effort
**7-9 weeks** including corpus collection, processing, synthetic generation, and management system implementation.