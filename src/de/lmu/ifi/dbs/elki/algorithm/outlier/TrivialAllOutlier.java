package de.lmu.ifi.dbs.elki.algorithm.outlier;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.AssociationID;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.result.AnnotationFromDataStore;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.ProbabilisticOutlierScore;

/**
 * Trivial method that claims all objects to be outliers. Can be used as
 * reference algorithm in comparisons.
 * 
 * @author Erich Schubert
 */
public class TrivialAllOutlier extends AbstractAlgorithm<OutlierResult> implements OutlierAlgorithm {
  /**
   * Our logger.
   */
  private static final Logging logger = Logging.getLogger(TrivialAllOutlier.class);

  /**
   * Association id to associate
   */
  public static final AssociationID<Double> TRIVIAL_ALL_OUT = AssociationID.getOrCreateAssociationID("trivial_alloutliers", Double.class);

  /**
   * Constructor.
   */
  public TrivialAllOutlier() {
    super();
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.ANY);
  }

  public OutlierResult run(@SuppressWarnings("unused") Database database, Relation<?> relation) throws IllegalStateException {
    WritableDataStore<Double> scores = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_HOT, Double.class);
    for(DBID id : relation.iterDBIDs()) {
      scores.put(id, 0.0);
    }
    AnnotationResult<Double> scoreres = new AnnotationFromDataStore<Double>("Trivial all-outlier score", "all-outlier", TRIVIAL_ALL_OUT, scores);
    OutlierScoreMeta meta = new ProbabilisticOutlierScore();
    return new OutlierResult(meta, scoreres);
  }

  @Override
  protected Logging getLogger() {
    return logger;
  }
}