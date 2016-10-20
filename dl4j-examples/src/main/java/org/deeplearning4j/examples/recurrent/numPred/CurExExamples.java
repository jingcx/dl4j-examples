package org.deeplearning4j.examples.recurrent.numPred;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.writable.Writable;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.deeplearning4j.examples.recurrent.basic.BasicRNNExample.LEARNSTRING_CHARS_LIST;

/**
 * Created by YoungH on 10/19/16.
 * This example trains a RNN. We take the history data of current exchange rate from CNY to One U.S. dollar as input,
 * , it will predict the rate of next time.
 */
public class CurExExamples {

    private static int HIDDEN_LAYER_WIDTH=6;
    private static int HIDDEN_LAYER_CONT=3;
    private static INDArray input;
    private static INDArray labels;
    private static List<Double> trainList = new ArrayList<Double>();
    private static List<Double> testList = new ArrayList<Double>();
    private static DataSet trainingData;


    public static void main(String[] args) throws IOException, InterruptedException {
        DataSet trainingData = loadData();
        NeuralNetConfiguration.ListBuilder listBuilder = bconfigureNN();
        MultiLayerNetwork net = creatMLP(listBuilder);


        // train the data
        net.fit(trainingData);
        // clear current stance from the last example
        net.rnnClearPreviousState();

        // put the first caracter into the rrn as an initialisation
        INDArray testInit = Nd4j.zeros(1);
        testInit.putScalar(0,testList.get(0));

        // run one step -> IMPORTANT: rnnTimeStep() must be called, not
        // output()
        // the output shows what the net thinks what should come next
        INDArray output = net.rnnTimeStep(testInit);

        System.out.println(output);

        // some epochs
        for (int epoch = 0; epoch < 100; epoch++) {
            System.out.println("Epoch " + epoch);
            // train the data
            net.fit(trainingData);

            // clear current stance from the last example
            net.rnnClearPreviousState();
            output = net.rnnTimeStep(testInit);

            System.out.println(output);
            System.out.println("***********************\n");
        }
    }

    private static MultiLayerNetwork creatMLP(NeuralNetConfiguration.ListBuilder listBuilder) {
        // create network
        MultiLayerConfiguration conf = listBuilder.build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new ScoreIterationListener(1));
        return net;
    }

    private static NeuralNetConfiguration.ListBuilder bconfigureNN() {
        // some common parameters
        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder();
        builder.iterations(10);
        builder.learningRate(0.001);
        builder.optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT);
        builder.seed(123);
        builder.biasInit(0);
        builder.miniBatch(false);
        builder.updater(Updater.RMSPROP);
        builder.weightInit(WeightInit.XAVIER);

        NeuralNetConfiguration.ListBuilder listBuilder = builder.list();

        // first difference, for rnns we need to use GravesLSTM.Builder
        for (int i = 0; i < HIDDEN_LAYER_CONT; i++) {
            GravesLSTM.Builder hiddenLayerBuilder = new GravesLSTM.Builder();
            hiddenLayerBuilder.nIn(i == 0 ? 1 : HIDDEN_LAYER_WIDTH);
            hiddenLayerBuilder.nOut(HIDDEN_LAYER_WIDTH);
            // adopted activation function from GravesLSTMCharModellingExample
            // seems to work well with RNNs
            hiddenLayerBuilder.activation("tanh");
            listBuilder.layer(i, hiddenLayerBuilder.build());
        }

        // we need to use RnnOutputLayer for our RNN
        RnnOutputLayer.Builder outputLayerBuilder = new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT);
        // softmax normalizes the output neurons, the sum of all outputs is 1
        // this is required for our sampleFromDistribution-function
        outputLayerBuilder.activation("softmax");
        outputLayerBuilder.nIn(HIDDEN_LAYER_WIDTH);
        outputLayerBuilder.nOut(1);
        listBuilder.layer(HIDDEN_LAYER_CONT, outputLayerBuilder.build());

        // finish builder
        listBuilder.pretrain(false);
        listBuilder.backprop(true);

        return listBuilder;
    }


    private static DataSet loadData() throws IOException, InterruptedException {


        RecordReader rr = new CSVRecordReader();
        rr.initialize(new FileSplit(new File("dl4j-examples/src/main/resources/curExc/training.csv")));


        RecordReader rrTest = new CSVRecordReader();
        rrTest.initialize(new FileSplit(new File("dl4j-examples/src/main/resources/curExc/test.csv")));

        while(rr.hasNext()){
            trainList.add(Double.parseDouble(rr.next().get(2).toString()));
//            trainList.add((int) (Double.parseDouble(rr.next().get(2).toString())*10000));
        }

        while(rrTest.hasNext()){
            testList.add(Double.parseDouble(rrTest.next().get(2).toString()));
//            trainList.add((int) (Double.parseDouble(rr.next().get(2).toString())*10000));
        }
        input = Nd4j.zeros(1,1, trainList.size());
        labels = Nd4j.zeros(1,1, trainList.size());

        for (int i=0;i<trainList.size();i++) {
            if (i==0){
                input.putScalar(new int[]{0,0,i},trainList.get(i));
            }else{
                input.putScalar(new int[]{0,0,i},trainList.get(i));
                labels.putScalar(new int[]{0,0,i-1},trainList.get(i));
            }
        }
        labels.putScalar(new int[]{0,0,trainList.size()-1},testList.get(0));
        System.out.println("loadData Done");

        DataSet trainingData = new DataSet(input, labels);
        return trainingData;
//        for (int i : trainList) {
//            int d0 = i/10000;
//            int d1 = (i-d0*10000)/1000;
//            int d2 = (i-d0*10000-d1*1000)/100;
//            int d3 = (i-d0*10000-d1*1000-d2*100)/10;
//            int d4 = (i-d0*10000-d1*1000-d2*100-d3*10);
//        }


    }
}
