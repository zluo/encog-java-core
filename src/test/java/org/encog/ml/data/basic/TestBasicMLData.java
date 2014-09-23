package org.encog.ml.data.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.encog.ml.data.MLData;
import org.encog.util.kmeans.Centroid;
import org.junit.Test;

public class TestBasicMLData {
  
/**
 * Test BasicMLData clone, plus
 */
@Test
public void testBasicData() {
  
  double[] da1= {1.0,2.0,3.0};
  double[] da2= {11.0,12.0,13.0};
  
  BasicMLData basicData1 = new BasicMLData(da1);
  BasicMLData basicData2 = new BasicMLData(da2);
  
  BasicMLData basicData3 = (BasicMLData) basicData2.clone();
  
  assertTrue(basicData3.getData()[0]==basicData2.getData()[0]);
  
  MLData basicData4 =((BasicMLData) basicData3).plus(basicData2); 
  
  assertEquals(basicData4.getData()[0],22.0,0.00001);
}


/**
 * Test Centriod 
 */
@Test
public void testBasicDataCentroid() {
  
  double[] da1= {1.0,2.0,3.0};
  double[] da2= {1.0,2.0,3.0};
  double[] da3= {3.0,4.0,5.0};
  double[] da4= {2.0,3.0,4.0};
  
  BasicMLData basicData1 = new BasicMLData(da1);
  BasicMLData basicData2 = new BasicMLData(da2);
  Centroid<MLData> centroid = basicData1.createCentroid();
  double d =centroid.distance(basicData2);
  assertEquals(0, d, 0.0001);
  
  centroid.add(new BasicMLData(da3));

  d =centroid.distance(new BasicMLData(da4));
  assertEquals(0, d, 0.0001);

  
  
}
}