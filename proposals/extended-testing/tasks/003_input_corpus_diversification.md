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
Develop a diverse corpus collection that represents various text processing scenarios, with particular emphasis on biology/bioinformatics data and established pattern matching benchmarks:

### Text Domain Categories

1. **Bioinformatics and Biological Sequence Corpus** ⭐
   - **DNA/RNA sequences**: NCBI GenBank sequences, human genome segments
   - **Protein sequences**: UniProt database entries, FASTA format files  
   - **Gene annotation data**: GTF/GFF files with regulatory patterns
   - **Multiple sequence alignments**: CLUSTAL and MUSCLE alignment outputs
   - **Phylogenetic data**: Newick format trees with complex naming patterns
   - **Biological literature**: PubMed abstracts with gene/protein mentions
   - **Size**: 100+ sequence files, ~1GB total
   - **Pattern characteristics**: High repetition, long exact matches, biological motifs

2. **Literature Corpus (Multiple Works)**
   - Classic literature: Pride and Prejudice, Alice in Wonderland, etc.
   - Modern literature: Recent public domain works  
   - Poetry: Various forms and structures
   - Multiple languages: French, German, Spanish public domain texts
   - **Size**: 50+ works, ~100MB total

3. **Technical Text Corpus**
   - Source code: Java, Python, JavaScript, C++ samples
   - Configuration files: XML, JSON, YAML formats
   - Documentation: Markdown, LaTeX, HTML
   - Log files: Web server, application, system logs
   - **Size**: 1000+ files, ~500MB total

4. **Structured Data Corpus**
   - CSV files with various delimiters and escaping
   - Tab-separated data with different structures
   - Semi-structured text: email formats, chat logs
   - Mixed format documents
   - **Size**: 500+ files, ~200MB total

5. **Established Benchmark Datasets**  
   - **Canterbury Corpus**: Standard compression benchmark texts
   - **Large Text Compression Benchmark**: Realistic large-scale data
   - **SMART Information Retrieval**: Document collection for IR research
   - **20 Newsgroups**: Classic text classification dataset
   - **Common Crawl samples**: Real web data segments
   - **Size**: Varies, up to 10GB total

6. **Unicode Stress Test Corpus**
   - Multi-byte character documents (Chinese, Japanese, Arabic)
   - Emoji-heavy social media text
   - Mathematical notation and symbols
   - Right-to-left text processing
   - **Size**: 100+ files, ~50MB total

7. **Synthetic Stress Test Corpus**
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
    BIOINFORMATICS,     // DNA/RNA/protein sequences, gene annotations
    LITERATURE,
    SOURCE_CODE,
    LOG_FILES,
    STRUCTURED_DATA,
    SOCIAL_MEDIA,
    TECHNICAL_DOCS,
    BENCHMARK_STANDARD, // Established benchmark datasets
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
    SPARSE_CONTENT,
    BIOLOGICAL_SEQUENCES,   // DNA/RNA patterns (ATCG, etc.)
    PROTEIN_MOTIFS,         // Amino acid sequences
    ANNOTATION_MARKERS,     // Gene annotation symbols
    HIGH_REPETITION,        // Common in biological data
    FIXED_WIDTH_FIELDS      // Common in bioinformatics formats
}
```

### Concrete Dataset Sources and Acquisition Methods

#### Bioinformatics Data Sources
1. **NCBI GenBank** - https://www.ncbi.nlm.nih.gov/genbank/
   - **Acquisition**: Use NCBI E-utilities API for programmatic access
   - **Formats**: FASTA, GenBank flat files
   - **GitHub Actions compatible**: REST API with rate limiting

2. **UniProt Protein Database** - https://www.uniprot.org/
   - **Acquisition**: Download via UniProt REST API
   - **Formats**: FASTA, XML, TSV
   - **Size**: Complete proteomes (~100MB-1GB per species)

3. **Ensembl Genome Browser** - https://www.ensembl.org/
   - **Acquisition**: FTP download of GTF/GFF annotation files
   - **Example**: Human GRCh38 annotations (~50MB compressed)
   - **Pattern complexity**: Rich annotation metadata

4. **1000 Genomes Project** - https://www.internationalgenome.org/
   - **Acquisition**: Public cloud buckets (AWS, Google Cloud)
   - **Formats**: VCF files with variant annotations
   - **GitHub Actions**: Use cloud CLI tools

#### Established Benchmark Collections
5. **Canterbury Corpus** - https://corpus.canterbury.ac.nz/
   - **Direct download**: Public FTP server
   - **Files**: alice29.txt, asyoulik.txt, cp.html, etc.
   - **Size**: ~3MB total, diverse characteristics

6. **Large Text Compression Benchmark** - http://mattmahoney.net/dc/textdata.html
   - **Files**: enwik8 (100MB), enwik9 (1GB) Wikipedia dumps
   - **GitHub Actions**: Wget/curl downloads

7. **SMART Collection** - ftp://ftp.cs.cornell.edu/pub/smart/
   - **Classic IR benchmark**: 1,033 documents
   - **Pattern testing**: Document retrieval scenarios

#### Programmatic Dataset Generation
```bash
# GitHub Actions compatible download script
#!/bin/bash
set -e

# Download Canterbury Corpus
wget -q http://corpus.canterbury.ac.nz/corpus/canterbury.tar.gz
tar -xzf canterbury.tar.gz

# Download sample genome data
wget -q "https://ftp.ensembl.org/pub/release-109/fasta/homo_sapiens/dna/Homo_sapiens.GRCh38.dna.chromosome.22.fa.gz"
gunzip Homo_sapiens.GRCh38.dna.chromosome.22.fa.gz

# Download UniProt sample
wget -q "https://rest.uniprot.org/uniprotkb/stream?query=organism_id:9606&format=fasta&size=1000" -O human_proteins_sample.fasta
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

1. **Bioinformatics Corpus Collection (Weeks 1-2)** ⭐
   - Set up NCBI E-utilities integration for sequence downloads
   - Implement UniProt API client for protein data acquisition  
   - Create genome annotation parsers for GTF/GFF files
   - Add biological sequence validators and format converters
   - **GitHub Actions**: Automated daily/weekly corpus updates

2. **Standard Benchmark Integration (Week 3)**
   - Download and integrate Canterbury Corpus
   - Add Large Text Compression Benchmark datasets
   - Integrate SMART collection for information retrieval patterns
   - Create benchmark dataset validation and checksum verification
   - **GitHub Actions**: Automated benchmark data integrity checks

3. **Corpus Processing and Metadata (Week 4)**
   - Implement text cleaning and normalization (preserve biological accuracy)
   - Create corpus metadata extraction with biological annotations
   - Build corpus indexing system with domain-specific features
   - Add file format detection for bioinformatics formats
   - **GitHub Actions**: Corpus processing pipeline

4. **Synthetic Generation (Weeks 5-6)**
   - Implement biological sequence generators (realistic DNA/protein patterns)
   - Create match density generators for all domains
   - Build stress test input generation
   - Add Unicode complexity generators
   - **GitHub Actions**: Synthetic data generation workflows

5. **Management System (Week 7)**
   - Implement corpus selection algorithms with domain weighting
   - Create corpus statistics analysis with biological metrics
   - Build corpus validation system
   - Add corpus update mechanisms with versioning
   - **GitHub Actions**: Automated corpus management

6. **Integration and Testing (Weeks 8-9)**
   - Integrate with JMH-based test framework (from Task 001)
   - Add corpus selection to test orchestration
   - Implement efficient streaming for large biological datasets
   - Create corpus management tools and CLI
   - **GitHub Actions**: End-to-end testing pipeline

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
- [ ] **Bioinformatics corpus integration complete**
  - [ ] NCBI GenBank sequence integration working
  - [ ] UniProt protein database integration functional
  - [ ] GTF/GFF genome annotation processing implemented
  - [ ] Biological sequence pattern validation in place
- [ ] **Established benchmark datasets integrated**
  - [ ] Canterbury Corpus fully integrated and tested
  - [ ] Large Text Compression Benchmark datasets available
  - [ ] SMART collection accessible for IR pattern testing
  - [ ] Benchmark data integrity verification implemented
- [ ] **Comprehensive domain coverage achieved**
  - [ ] 7+ text domains represented with adequate coverage
  - [ ] Corpus management system fully operational with biological metadata
  - [ ] Synthetic generation for all stress test scenarios including biological
  - [ ] Unicode complexity coverage implemented
- [ ] **Technical infrastructure complete**
  - [ ] Corpus selection algorithms working correctly with domain priorities
  - [ ] Performance impact of corpus loading < 10% overhead
  - [ ] Integration with JMH-based test framework complete
  - [ ] Full GitHub Actions workflow compatibility verified
  - [ ] Documentation for corpus contributors with biological data guidelines

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
**8-9 weeks** including bioinformatics corpus integration, established benchmark dataset integration, corpus processing, synthetic generation, and management system implementation with full GitHub Actions compatibility.