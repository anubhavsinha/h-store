package org.voltdb.regressionsuites;

import java.io.File;

import junit.framework.Test;

import org.voltdb.BackendTarget;
import org.voltdb.SysProcSelector;
import org.voltdb.VoltSystemProcedure;
import org.voltdb.VoltTable;
import org.voltdb.benchmark.tpcc.TPCCConstants;
import org.voltdb.benchmark.tpcc.TPCCProjectBuilder;
import org.voltdb.benchmark.tpcc.procedures.neworder;
import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;
import org.voltdb.sysprocs.AdHoc;
import org.voltdb.sysprocs.SetConfiguration;
import org.voltdb.sysprocs.Statistics;
import org.voltdb.types.TimestampType;
import org.voltdb.utils.VoltTableUtil;

import edu.brown.hstore.Hstoreservice.Status;
import edu.brown.mappings.ParametersUtil;
import edu.brown.rand.DefaultRandomGenerator;
import edu.brown.utils.ProjectType;

/**
 * Simple test suite for the TM1 benchmark
 * @author pavlo
 */
public class TestMarkovSuite extends RegressionSuite {
    
    private static final String PREFIX = "markov";
    private static final double SCALEFACTOR = 0.0001;
    private static final DefaultRandomGenerator rng = new DefaultRandomGenerator(0);
    
    
    /**
     * Constructor needed for JUnit. Should just pass on parameters to superclass.
     * @param name The name of the method to test. This is just passed to the superclass.
     */
    public TestMarkovSuite(String name) {
        super(name);
    }
    
    private Object[] generateNewOrder(int num_warehouses, short w_id, boolean dtxn) throws Exception {
        short supply_w_id;
        if (dtxn) {
            supply_w_id = (short)rng.numberExcluding(TPCCConstants.STARTING_WAREHOUSE, num_warehouses, w_id);
            assert(supply_w_id != w_id);
        } else {
            supply_w_id = (short)w_id;
        }
        
        // ORDER_LINE ITEMS
        int num_items = rng.number(TPCCConstants.MIN_OL_CNT, TPCCConstants.MAX_OL_CNT);
        int item_ids[] = new int[num_items];
        short supwares[] = new short[num_items];
        int quantities[] = new int[num_items];
        for (int i = 0; i < num_items; i++) { 
            item_ids[i] = rng.nextInt((int)(TPCCConstants.NUM_ITEMS));
            supwares[i] = (i % 2 == 0 ? supply_w_id : w_id);
            quantities[i] = 1;
        } // FOR
        
        byte d_id = (byte)rng.number(1, TPCCConstants.DISTRICTS_PER_WAREHOUSE);
        Object params[] = {
            w_id,               // W_ID
            d_id,               // D_ID
            1,                  // C_ID
            new TimestampType(),// TIMESTAMP
            item_ids,           // ITEM_IDS
            supwares,           // SUPPLY W_IDS
            quantities          // QUANTITIES
        };
        return (params);
    }
    
    /**
     * testInitialize
     */
    public void testInitialize() throws Exception {
        Client client = this.getClient();
        RegressionSuiteUtil.initializeTPCCDatabase(this.getCatalog(), client);
        
        String procName = VoltSystemProcedure.procCallName(AdHoc.class);
        for (String tableName : TPCCConstants.TABLENAMES) {
            String query = "SELECT COUNT(*) FROM " + tableName;
            ClientResponse cresponse = client.callProcedure(procName, query);
            assertEquals(Status.OK, cresponse.getStatus());
            VoltTable results[] = cresponse.getResults();
            assertEquals(1, results.length);
            long count = results[0].asScalarLong();
            assertTrue(tableName + " -> " + count, count > 0);
            System.err.println(tableName + "\n" + VoltTableUtil.format(results[0]));
        } // FOR
    }
    
    /**
     * testSinglePartitionCaching
     */
    public void testSinglePartitionCaching() throws Exception {
        Client client = this.getClient();
        RegressionSuiteUtil.initializeTPCCDatabase(this.getCatalog(), client);

        // Enable the feature on the server
        String procName = VoltSystemProcedure.procCallName(SetConfiguration.class);
        String confParams[] = {"site.markov_path_caching"};
        String confValues[] = {"true"};
        ClientResponse cresponse = client.callProcedure(procName, confParams, confValues);
        assertNotNull(cresponse);
        assertEquals(Status.OK, cresponse.getStatus());
        
        // Fire off a single-partition txn
        // It should always come back with zero restarts
        procName = neworder.class.getSimpleName();
        Object params[] = this.generateNewOrder(2, (short)1, false);
        cresponse = client.callProcedure(procName, params);
        assertEquals(cresponse.toString(), Status.OK, cresponse.getStatus());
        assertTrue(cresponse.toString(), cresponse.isSinglePartition());
        assertEquals(cresponse.toString(), 0, cresponse.getRestartCounter());
        
        // Then execute the same thing again. It should use the cache estimate
        // from the previous txn
        // We have to generate new params so that we get different item ids
        params = this.generateNewOrder(2, (short)1, false);
        cresponse = client.callProcedure(procName, params);
        assertEquals(cresponse.toString(), Status.OK, cresponse.getStatus());
        assertTrue(cresponse.toString(), cresponse.isSinglePartition());
        assertEquals(cresponse.toString(), 0, cresponse.getRestartCounter());
        
        // So we need to grab the MarkovEstimatorProfiler stats and check 
        // that the cache counter is greater than one
        procName = VoltSystemProcedure.procCallName(Statistics.class);
        params = new Object[]{ SysProcSelector.MARKOVPROFILER.name(), 0 };
        cresponse = client.callProcedure(procName, params);
        assertEquals(cresponse.toString(), Status.OK, cresponse.getStatus());
        VoltTable results[] = cresponse.getResults();
        boolean found = false;
        while (results[0].advanceRow()) {
            for (int i = 0; i < results[0].getColumnCount(); i++) {
                String col = results[0].getColumnName(i).toUpperCase();
                if (col.endsWith("_CNT") && col.contains("CACHE")) {
                    long cnt = results[0].getLong(i);
                    if (cnt > 0) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
        } // WHILE
        System.err.println(VoltTableUtil.format(results[0]));

        // FIXME assertTrue(found);
    }
    
    /**
     * testDistributedTxn
     */
    public void testDistributedTxn() throws Exception {
        Client client = this.getClient();
        RegressionSuiteUtil.initializeTPCCDatabase(this.getCatalog(), client);

        // Fire off a distributed neworder txn
        // It should always come back with zero restarts
        String procName = neworder.class.getSimpleName();
        Object params[] = this.generateNewOrder(2, (short)1, true);
        
        ClientResponse cresponse = client.callProcedure(procName, params);
        assertEquals(cresponse.toString(), Status.OK, cresponse.getStatus());
        assertFalse(cresponse.toString(), cresponse.isSinglePartition());
        assertEquals(cresponse.toString(), 0, cresponse.getRestartCounter());
//        System.err.println(cresponse);
        
        // Get the MarkovEstimatorProfiler stats
        procName = VoltSystemProcedure.procCallName(Statistics.class);
        params = new Object[]{ SysProcSelector.MARKOVPROFILER.name(), 0 };
        cresponse = client.callProcedure(procName, params);
        assertEquals(cresponse.toString(), Status.OK, cresponse.getStatus());
        System.err.println(VoltTableUtil.format(cresponse.getResults()[0]));
    }
    
    public static Test suite() throws Exception {
        File mappings = ParametersUtil.getParameterMappingsFile(ProjectType.TPCC);
        File markovs = new File("files/markovs/vldb-august2012/tpcc-2p.markov.gz"); // HACK
        
        VoltServerConfig config = null;
        // the suite made here will all be using the tests from this class
        MultiConfigSuiteBuilder builder = new MultiConfigSuiteBuilder(TestMarkovSuite.class);
        builder.setGlobalConfParameter("client.scalefactor", SCALEFACTOR);
        builder.setGlobalConfParameter("site.specexec_enable", true);
        builder.setGlobalConfParameter("site.specexec_idle", true);
        builder.setGlobalConfParameter("site.specexec_ignore_all_local", false);
        builder.setGlobalConfParameter("site.network_txn_initialization", true);
        builder.setGlobalConfParameter("site.markov_enable", true);
        builder.setGlobalConfParameter("site.markov_profiling", true);
        builder.setGlobalConfParameter("site.markov_path_caching", true);
        builder.setGlobalConfParameter("site.markov_path", markovs.getAbsolutePath());

        // build up a project builder for the TPC-C app
        TPCCProjectBuilder project = new TPCCProjectBuilder();
        project.addDefaultSchema();
        project.addDefaultProcedures();
        project.addDefaultPartitioning();
        project.addParameterMappings(mappings);
        
        boolean success;
        
        /////////////////////////////////////////////////////////////
        // CONFIG #1: 1 Local Site with 2 Partitions running on JNI backend
        /////////////////////////////////////////////////////////////
        config = new LocalSingleProcessServer(PREFIX + "-2part.jar", 2, BackendTarget.NATIVE_EE_JNI);
        success = config.compile(project);
        assert(success);
        builder.addServerConfig(config);

        ////////////////////////////////////////////////////////////
        // CONFIG #2: cluster of 2 nodes running 1 site each, one replica
        ////////////////////////////////////////////////////////////
        config = new LocalCluster(PREFIX + "-cluster.jar", 2, 1, 1, BackendTarget.NATIVE_EE_JNI);
        success = config.compile(project);
        assert(success);
        builder.addServerConfig(config);

        return builder;
    }

}