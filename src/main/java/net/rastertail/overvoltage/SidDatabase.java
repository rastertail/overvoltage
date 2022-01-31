package net.rastertail.overvoltage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;

import libsidplay.sidtune.SidTune;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A searchable database of SID tunes */
public class SidDatabase {
    /** Metadata associated with a SID tune */
    public class SidInfo {
        /** Title of the tune */
        public String title;

        /** Artist of the tune */
        public String artist;

        /** Release info for the tune */
        public String released;

        /** HVSC path of the tune */
        public Path path;

        /** Retrieve SID tune info from a search document */
        public SidInfo(Document doc) {
            this.title = doc.get(TITLE_FIELD);
            this.artist = doc.get(ARTIST_FIELD);
            this.released = doc.get(RELEASED_FIELD);
            this.path = Paths.get(doc.get(PATH_FIELD));
        }
    }
    
    /** Logger for this class */
    private final Logger LOG = LoggerFactory.getLogger(SidDatabase.class);
    
    /** Field ID for tune titles */
    private static final String TITLE_FIELD = "title";

    /** Field ID for tune artists */
    private static final String ARTIST_FIELD = "artist";

    /** Field ID for tune release info */
    private static final String RELEASED_FIELD = "released";

    /** Field ID for tune path within the HVSC */
    private static final String PATH_FIELD = "path";

    /** Threshold for relevant results */
    private static final float RELEVANCY_THRESH = 1.5f;

    /** The base path under which all SID tunes are located */
    private Path basePath;

    /** The Lucene analyzer used for this database */
    private Analyzer analyzer;

    /** The reader over the search index directory */
    private DirectoryReader indexReader;

    /** The search index searcher */
    private IndexSearcher searcher;

    /** The search query parser */
    private StandardQueryParser queryParser;

    /**
     * Construct a new SID tune database, 
     *
     * @param basePath the path to load tunes from
     * @param indexDir the search index directory
     * @param reindex whether or not to force reindexing
     *
     * @throws IOException if loading the index fails
     */
    public SidDatabase(Path basePath, Directory indexDir, boolean reindex) throws IOException {
        // Base setup
        this.basePath = basePath;
        this.analyzer = new StandardAnalyzer();
        
        // Build an index if one does not already exist
        if (!DirectoryReader.indexExists(indexDir) || reindex) {
            LOG.info("Indexing SID tunes...");

            // Create index writer
            IndexWriterConfig config = new IndexWriterConfig(this.analyzer);
            IndexWriter writer = new IndexWriter(indexDir, config);

            // Index all tunes within base directory
            Files.walk(basePath)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        // Load SID tune and extract metadata
                        SidTune tune = SidTune.load(path.toFile());
                        String[] info = tune
                            .getInfo()
                            .getInfoString()
                            .toArray(new String[] {});

                        // Resolve relative HVSC path
                        Path hvscPath = basePath.relativize(path);

                        // Create and insert Lucene document
                        Document doc = new Document();
                        doc.add(new Field(TITLE_FIELD, info[0], TextField.TYPE_STORED));
                        doc.add(new Field(ARTIST_FIELD, info[1], TextField.TYPE_STORED));
                        doc.add(new Field(RELEASED_FIELD, info[2], TextField.TYPE_STORED));
                        doc.add(new Field(PATH_FIELD, hvscPath.toString(), TextField.TYPE_STORED));

                        writer.addDocument(doc);
                    } catch (Exception e) {
                        LOG.warn("Failed to index SID at path {}: {}", path, e);
                    }
                });

            writer.close();
        }

        // Initialize reader
        this.indexReader = DirectoryReader.open(indexDir);
        this.searcher = new IndexSearcher(this.indexReader);

        // Initialize query parser
        this.queryParser = new StandardQueryParser();
        this.queryParser.setAnalyzer(this.analyzer);

        LOG.info("SID database initialized");
    }

    /**
     * Query the database for tunes
     *
     * @param query the query string in Lucene query language
     *
     * @throws IOException if the search index is inaccessible
     * @throws QueryNodeException if the query is malformed
     *
     * @return a list of relevant results
     */
    public ArrayList<SidInfo> search(String query) throws IOException, QueryNodeException {
        LOG.debug("Searching for `{}`", query);

        // Construct query
        Query q = this.queryParser.parse(query, TITLE_FIELD);
        
        // Perform search
        ScoreDoc[] hits = this.searcher.search(q, 10).scoreDocs;

        // Aggregate relevant results
        ArrayList<SidInfo> relevant = new ArrayList<SidInfo>();
        if (hits.length > 0) {
            float topScore = hits[0].score;
            for (ScoreDoc score_doc : hits) {
                if (score_doc.score > topScore - RELEVANCY_THRESH) {
                    Document doc = this.searcher.doc(score_doc.doc);
                    relevant.add(new SidInfo(doc));
                }
            }
        }

        return relevant;
    }
}
