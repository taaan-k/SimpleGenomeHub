Legacy CDS strand regression fixtures
=====================================
The fixture is hand specified, not produced by SequenceExtractor.
All coordinates below are 1-based inclusive. Every CDS phase is 0 and each segment
length is divisible by 3. No partial CDS, ambiguous base, phase handling or promoter
rule is involved. Introns and flanks are deliberately nonuniform.

Expected complete long ORF (18 nt): ATG AAA CCC GGG TTT TAA
Expected long protein:             M   K   P   G   F   *
Expected complete short ORF (12 nt): ATG AAA TTT TAA
Expected short protein:             M   K   F   *

Genome positions / literal plus-reference sequence
pSingleChr 7..24: ATGAAACCCGGGTTTTAA  (+ CDS)
mSingleChr 7..24: TTAAAACCCGGGTTTCAT  (- CDS; reverse complement is long ORF)
pMultiChr: ATGAAACCCCGTACGGGTTTTAAACCGTTAG
mMultiChr: TTAAAACCCCGTACGGGTTTCATACCGTTAG
pLong: 1..9 ATGAAACCC; 15..23 GGGTTTTAA (+)
mLong: 1..9 TTAAAACCC; 15..23 GGGTTTCAT (-)
pShort: 1..6 ATGAAA; 18..23 TTTTAA (+)
mShort: 1..6 TTAAAA; 18..23 TTTCAT (-)
The negative long transcript runs 15..23 -> 1..9. Reverse complementing those two
segments in that order gives ATGAAACCC + GGGTTTTAA.
Sorting negative segments in ascending genomic order instead gives
GGGTTTTAA + ATGAAACCC = GGGTTTTAAATGAAACCC, which is wrong and translates to GF*.
The negative short transcript likewise runs 18..23 -> 1..6.

Models
pSingleGene has pSingle (one CDS, 18 nt).
mSingleGene has mSingle (one CDS, 18 nt).
pMultiGene has pLong (two 9-nt CDS, 18 nt) and pShort (two 6-nt CDS, 12 nt).
mMultiGene has mLong (two 9-nt CDS, 18 nt) and mShort (two 6-nt CDS, 12 nt).
LONGEST_CDS must retain pLong/mLong and omit pShort/mShort. The short isoforms are
complete distinct ORFs, so selection is tested rather than implied by one transcript.

Output ID conventions are existing API behavior, not changed by this regression:
extractCDS / extractProteins: transcript ID (e.g. mLong).
extractRepresentativeCDS: transcript ID + _cds (e.g. mLong_cds).
extractRepresentativeProteins: transcript ID (e.g. mLong).

The second pass reverses all CDS feature rows while preserving other rows, including
parent gene/mRNA declarations. This tests input CDS ordering without introducing the
unrelated single-pass parser question of children preceding parent declarations.
Generated FASTA and the reversed GFF go to one unique run folder. Production helper
temporary GFF files use its unchanged temporary-file mechanism. The fixtures are
only read. All four API failures are collected before the regression exits with
AssertionError.
