package services;

import java.io.*;
import java.util.Random;

public class NeuralNetwork implements Serializable {

    private static final long serialVersionUID = 1L;

    // Architecture
    private final int inputSize = 150;   // 30 candles * 5 features
    private final int hiddenSize = 128;
    private final int outputSize = 3;    // Buy, Hold, Sell

    // Weights & Biases
    private double[][] W1 = new double[inputSize][hiddenSize];
    private double[] b1 = new double[hiddenSize];

    private double[][] W2 = new double[hiddenSize][outputSize];
    private double[] b2 = new double[outputSize];

    private double learningRate = 0.001;
    private double buyLearningScale = 2.5;
    private double holdLearningScale = 0.35;
    private double sellLearningScale = 2.5;

    public NeuralNetwork() {
        initWeights();
    }

    private void initWeights() {
        Random rand = new Random();
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                W1[i][j] = rand.nextGaussian() * 0.01;
            }
        }
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                W2[i][j] = rand.nextGaussian() * 0.01;
            }
        }
    }

    private double relu(double x) {
        return Math.max(0, x);
    }

    private double reluDerivative(double x) {
        return x > 0 ? 1 : 0;
    }

    private double[] softmax(double[] z) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : z) max = Math.max(max, v);

        double sum = 0.0;
        double[] exp = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            exp[i] = Math.exp(z[i] - max);
            sum += exp[i];
        }

        for (int i = 0; i < z.length; i++) {
            exp[i] /= sum;
        }

        return exp;
    }

    // Forward propogation
    public double[] predict(double[][] candles30) {
        double[] input = flatten(candles30);

        double[] z1 = new double[hiddenSize];
        double[] a1 = new double[hiddenSize];

        for (int j = 0; j < hiddenSize; j++) {
            for (int i = 0; i < inputSize; i++) {
                z1[j] += input[i] * W1[i][j];
            }
            z1[j] += b1[j];
            a1[j] = relu(z1[j]);
        }

        double[] z2 = new double[outputSize];
        for (int j = 0; j < outputSize; j++) {
            for (int i = 0; i < hiddenSize; i++) {
                z2[j] += a1[i] * W2[i][j];
            }
            z2[j] += b2[j];
        }

        return softmax(z2);
    }

    private double getClassLearningScale(double[] target) {
		if (target[0] == 1.0)
			return buyLearningScale;
		if (target[2] == 1.0)
			return sellLearningScale;
		return holdLearningScale;
    }

    // Training
    public boolean train(double[][] candles35) {
        // Split input and future
        double[][] inputCandles = new double[30][5];
        double[][] futureCandles = new double[5][5];

        System.arraycopy(candles35, 0, inputCandles, 0, 30);
        System.arraycopy(candles35, 30, futureCandles, 0, 5);

        double[] input = flatten(inputCandles);

        // Label generation
        double currentPrice = inputCandles[29][3]; // close
        double futurePrice = futureCandles[4][3];  // close

        double change = (futurePrice - currentPrice) / currentPrice;

        double[] target = new double[3]; // Buy, Hold, Sell

        if (change > 0.002) {
            target[0] = 1; // Buy
        } else if (change < -0.002) {
            target[2] = 1; // Sell
        } else {
            target[1] = 1; // Hold
        }

        double classLearningScale = getClassLearningScale(target);
        double effectiveLearningRate = learningRate * classLearningScale;

		// Used in forward propogation
        double[] z1 = new double[hiddenSize];
        double[] a1 = new double[hiddenSize];

        for (int j = 0; j < hiddenSize; j++) {
            for (int i = 0; i < inputSize; i++) {
                z1[j] += input[i] * W1[i][j];
            }
            z1[j] += b1[j];
            a1[j] = relu(z1[j]);
        }

        double[] z2 = new double[outputSize];
        for (int j = 0; j < outputSize; j++) {
            for (int i = 0; i < hiddenSize; i++) {
                z2[j] += a1[i] * W2[i][j];
            }
            z2[j] += b2[j];
        }

        double[] output = softmax(z2);

        // Used in backpropogation
        double[] dZ2 = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            dZ2[i] = output[i] - target[i];
        }

        double[][] dW2 = new double[hiddenSize][outputSize];
        double[] db2 = new double[outputSize];

        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                dW2[i][j] = a1[i] * dZ2[j];
            }
        }

        for (int i = 0; i < outputSize; i++) {
            db2[i] = dZ2[i];
        }

        double[] dA1 = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                dA1[i] += W2[i][j] * dZ2[j];
            }
        }

        double[] dZ1 = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            dZ1[i] = dA1[i] * reluDerivative(z1[i]);
        }

        double[][] dW1 = new double[inputSize][hiddenSize];
        double[] db1 = new double[hiddenSize];

        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                dW1[i][j] = input[i] * dZ1[j];
            }
        }

        for (int i = 0; i < hiddenSize; i++) {
            db1[i] = dZ1[i];
        }

        // Used to update weights and biases
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                W1[i][j] -= effectiveLearningRate * dW1[i][j];
            }
        }

        for (int i = 0; i < hiddenSize; i++) {
            b1[i] -= effectiveLearningRate * db1[i];
        }

        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                W2[i][j] -= effectiveLearningRate * dW2[i][j];
            }
        }

        for (int i = 0; i < outputSize; i++) {
            b2[i] -= effectiveLearningRate * db2[i];
        }

        return true;
    }

	// Used to save and load neural network
    public void save(String filePath) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath));
        oos.writeObject(this);
        oos.close();
    }

    public static NeuralNetwork load(String filePath) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath));
        NeuralNetwork nn = (NeuralNetwork) ois.readObject();
        ois.close();
        return nn;
    }

	// Flatten the 2d array into 1d for inputting into neural network
    private double[] flatten(double[][] candles) {
        double[] flat = new double[inputSize];
        int idx = 0;
        for (double[] candle : candles) {
            for (double v : candle) {
                flat[idx++] = v;
            }
        }
        return flat;
    }
}
