package experimentalcode.hettab.outlier;

import java.util.ArrayList;
import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.AbstractDistanceBasedAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.OutlierAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.spatial.neighborhood.NeighborSetPredicate;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.AssociationID;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.DoubleMinMax;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Matrix;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.result.AnnotationFromDataStore;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.QuotientOutlierScoreMeta;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;
import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;

/**
 * FIXME: Documentation, Reference
 * 
 * @author Ahmed Hettab
 * 
 * @param <V>
 * @param <D>
 */
public class RandomWalkEC<V extends NumberVector<?, ?>, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm<V, D, OutlierResult> implements OutlierAlgorithm {
  /**
   * Logger
   */
  private static final Logging logger = Logging.getLogger(RandomWalkEC.class);

  /**
   * Parameter to specify the neighborhood predicate to use.
   */
  public static final OptionID NEIGHBORHOOD_ID = OptionID.getOrCreateOptionID("neighborhood", "The neighborhood predicate to use.");

  /**
   * Holds the alpha value
   */
  public static final OptionID ALPHA_ID = OptionID.getOrCreateOptionID("rwec.alpha", "parameter for similarity computing");

  /**
   * Holds the z value
   */
  public static final OptionID Z_ID = OptionID.getOrCreateOptionID("rwec.z", "the position of z attribut");

  /**
   * Holds the c value
   */
  public static final OptionID C_ID = OptionID.getOrCreateOptionID("rwec.c", "the damping factor");

  /**
   * parameter alpha
   */
  private double alpha;

  /**
   * parameter z
   */
  private int z;

  /**
   * parameter c
   */
  private double c;

  /**
   * Our predicate to obtain the neighbors
   */
  NeighborSetPredicate.Factory<V> npredf = null;

  /**
   * The association id to associate the SCORE of an object for the RandomWalkEC
   * algorithm algorithm.
   */
  public static final AssociationID<Double> RW_EC_SCORE = AssociationID.getOrCreateAssociationID("outlier-score", TypeUtil.DOUBLE);

  /**
   * Constructor
   * 
   * @param distanceFunction
   * @param npredf
   * @param alpha
   * @param c
   * @param z
   */
  public RandomWalkEC(DistanceFunction<V, D> distanceFunction, NeighborSetPredicate.Factory<V> npredf, double alpha, double c, int z) {
    super(distanceFunction);
    this.npredf = npredf;
    this.alpha = alpha;
    this.z = z;
    this.c = c;
  }

  public OutlierResult run(Database database, Relation<V> relation) {
    final NeighborSetPredicate npred = npredf.instantiate(relation);
    DistanceQuery<V, D> distFunc = database.getDistanceQuery(relation, getDistanceFunction());
    WritableDataStore<Matrix> similarityVectors = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP, Matrix.class);
    WritableDataStore<List<Pair<DBID, Double>>> simScores = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP, List.class);

    // construct the relation Matrix of the ec-graph
    Matrix E = new Matrix(relation.size(), relation.size());
    int i = 0;
    for(DBID id : relation.iterDBIDs()) {
      npred.getNeighborDBIDs(id);
      int j = 0;
      for(DBID n : relation.iterDBIDs()) {
        double e;
        if(n.getIntegerID() == id.getIntegerID()) {
          e = 0;
        }
        else {
          double dist = distFunc.distance(id, n).doubleValue();
          double diff = Math.abs(relation.get(id).doubleValue(z) - relation.get(n).doubleValue(z));
          diff = (Math.pow(diff, alpha));
          diff = (Math.exp(diff));
          diff = (1 / diff);
          e = diff * dist;
        }
        E.set(i, j, e);
        j++;
      }
      i++;
    }
    // normalize the adjacent Matrix
    E.normalizeColumns();
    E.times(c);
    Matrix temp = Matrix.identity(relation.size(), relation.size());
    temp.minus(E);
    temp.times((1 - c));
    Matrix w = temp.inverse();

    // compute similarity vector for each Object
    int count = 0;
    for(DBID id : relation.iterDBIDs()) {
      Matrix Si = new Matrix(1, relation.size());
      Matrix Ei = new Matrix(relation.size(), 1);
      Si.transpose();
      // construct Ei
      for(int l = 0; l < relation.size(); l++) {
        if(l == count) {
          Ei.set(l, 0, 1.0);
        }
        else {
          Ei.set(l, 0, 0.0);
        }
      }
      Ei.transpose();
      Si = w.times(Ei);
      similarityVectors.put(id, Si);
      count++;
    }

    // compute the relevance scores between specified objects and its neighbors
    for(DBID id : relation.iterDBIDs()) {
      DBIDs neighbors = npred.getNeighborDBIDs(id);
      ArrayList<Pair<DBID, Double>> sim = new ArrayList<Pair<DBID, Double>>();
      for(DBID n : neighbors) {
        Pair<DBID, Double> p = new Pair<DBID, Double>(n, cosineSimilarity(similarityVectors.get(id).getColumnVector(0), similarityVectors.get(n).getColumnVector(0)));
        sim.add(p);
      }
      simScores.put(id, sim);
    }

    DoubleMinMax minmax = new DoubleMinMax();
    WritableDataStore<Double> scores = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_STATIC, Double.class);
    for(DBID id : relation.iterDBIDs()) {
      List<Pair<DBID, Double>> simScore = simScores.get(id);
      double score = 1;
      for(Pair<DBID, Double> pair : simScore) {
        score *= pair.second;
      }
      // System.out.println(id.getIntegerID() + ":" + score);
      scores.put(id, score);
      minmax.put(score);
    }
    Relation<Double> scoreResult = new AnnotationFromDataStore<Double>("randomwalkec", "RandomWalkEC", RW_EC_SCORE, scores, relation.getDBIDs());
    OutlierScoreMeta scoreMeta = new QuotientOutlierScoreMeta(minmax.getMin(), minmax.getMax(), 0.0, Double.POSITIVE_INFINITY, 0.0);
    return new OutlierResult(scoreMeta, scoreResult);
  }

  /**
   * Computes the cosine similarity for two given feature vectors.
   */
  private static double cosineSimilarity(Vector v1, Vector v2) {
    double p = 0;
    double p1 = 0;
    double p2 = 0;
    for(int i = 0; i < v1.getDimensionality(); i++) {
      p += v1.get(i) * v2.get(i);
      p1 += v1.get(i) * v1.get(i);
      p2 += v2.get(i) * v2.get(i);
    }
    return (p / (Math.sqrt(p1) * Math.sqrt(p2)));
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.NUMBER_VECTOR_FIELD);
  }

  @Override
  protected Logging getLogger() {
    return logger;
  }

  /**
   * Parameterization class.
   * 
   * FIXME: Documentation
   * 
   * @author Ahmed Hettab
   * 
   * @apiviz.exclude
   * 
   * @param <V>
   * @param <D>
   */
  public static class Parameterizer<V extends NumberVector<?, ?>, D extends NumberDistance<D, ?>> extends AbstractDistanceBasedAlgorithm.Parameterizer<V, D> {
    NeighborSetPredicate.Factory<V> npred = null;

    double alpha = 0.0;

    int z = 0;

    double c = 0.0;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      configNeighborPredicate(config);
      configAlpha(config);
      configZ(config);
      configC(config);
    }

    /**
     * Get the alpha parameter
     * 
     * @param config Parameterization
     * @return alpha parameter
     */
    protected void configAlpha(Parameterization config) {
      final DoubleParameter param = new DoubleParameter(ALPHA_ID);
      if(config.grab(param)) {
        alpha = param.getValue();
      }
    }

    /**
     * get the c parameter
     * 
     * @param config
     * @return
     */
    protected void configC(Parameterization config) {
      final DoubleParameter param = new DoubleParameter(C_ID);
      if(config.grab(param)) {
        c = param.getValue();
      }
    }

    /**
     * Get the z parameter
     * 
     * @param config Parameterization
     * @return z parameter
     */
    protected void configZ(Parameterization config) {
      final IntParameter param = new IntParameter(Z_ID);
      if(config.grab(param)) {
        z = param.getValue();
      }
    }

    /**
     * 
     * @param config
     * @return
     */
    protected void configNeighborPredicate(Parameterization config) {
      final ObjectParameter<NeighborSetPredicate.Factory<V>> param = new ObjectParameter<NeighborSetPredicate.Factory<V>>(NEIGHBORHOOD_ID, NeighborSetPredicate.Factory.class, true);
      if(config.grab(param)) {
        npred = param.instantiateClass(config);
      }
    }

    @Override
    protected RandomWalkEC<V, D> makeInstance() {
      return new RandomWalkEC<V, D>(distanceFunction, npred, alpha, c, z);
    }
  }
}