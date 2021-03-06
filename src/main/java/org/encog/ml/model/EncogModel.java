package org.encog.ml.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.encog.EncogError;
import org.encog.NullStatusReportable;
import org.encog.StatusReportable;
import org.encog.mathutil.randomize.generate.MersenneTwisterGenerateRandom;
import org.encog.ml.MLClassification;
import org.encog.ml.MLMethod;
import org.encog.ml.MLRegression;
import org.encog.ml.TrainingImplementationType;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.cross.DataFold;
import org.encog.ml.data.cross.KFoldCrossvalidation;
import org.encog.ml.data.versatile.MatrixMLDataSet;
import org.encog.ml.data.versatile.VersatileMLDataSet;
import org.encog.ml.data.versatile.columns.ColumnDefinition;
import org.encog.ml.data.versatile.columns.ColumnType;
import org.encog.ml.data.versatile.division.DataDivision;
import org.encog.ml.factory.MLMethodFactory;
import org.encog.ml.factory.MLTrainFactory;
import org.encog.ml.model.config.FeedforwardConfig;
import org.encog.ml.model.config.MethodConfig;
import org.encog.ml.model.config.NEATConfig;
import org.encog.ml.model.config.PNNConfig;
import org.encog.ml.model.config.RBFNetworkConfig;
import org.encog.ml.model.config.SVMConfig;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.end.SimpleEarlyStoppingStrategy;
import org.encog.util.Format;
import org.encog.util.simple.EncogUtility;

/**
 * Encog model is designed to allow you to easily swap between different model
 * types and automatically normalize data.  It is designed to work with a
 * VersatileMLDataSet only.
 */
public class EncogModel {

	private final VersatileMLDataSet dataset;
	private final List<ColumnDefinition> inputFeatures = new ArrayList<ColumnDefinition>();
	private final List<ColumnDefinition> predictedFeatures = new ArrayList<ColumnDefinition>();
	private MatrixMLDataSet trainingDataset;
	private MatrixMLDataSet validationDataset;
	private final Map<String, MethodConfig> methodConfigurations = new HashMap<String, MethodConfig>();
	private MethodConfig config;
	private String methodType;
	private String methodArgs;
	private String trainingType;
	private String trainingArgs;
	private StatusReportable report = new NullStatusReportable();

	public EncogModel(VersatileMLDataSet theDataset) {
		this.dataset = theDataset;
		this.methodConfigurations.put(MLMethodFactory.TYPE_FEEDFORWARD,
				new FeedforwardConfig());
		this.methodConfigurations
				.put(MLMethodFactory.TYPE_SVM, new SVMConfig());
		this.methodConfigurations.put(MLMethodFactory.TYPE_RBFNETWORK,
				new RBFNetworkConfig());
		this.methodConfigurations.put(MLMethodFactory.TYPE_NEAT,
				new NEATConfig());
		this.methodConfigurations
				.put(MLMethodFactory.TYPE_PNN, new PNNConfig());
	}

	/**
	 * @return the dataset
	 */
	public VersatileMLDataSet getDataset() {
		return dataset;
	}

	/**
	 * @return the inputFeatures
	 */
	public List<ColumnDefinition> getInputFeatures() {
		return inputFeatures;
	}

	/**
	 * @return the predictedFeatures
	 */
	public List<ColumnDefinition> getPredictedFeatures() {
		return predictedFeatures;
	}

	public void holdBackValidation(double validationPercent, boolean shuffle,
			int seed) {
		List<DataDivision> dataDivisionList = new ArrayList<DataDivision>();
		dataDivisionList.add(new DataDivision(1.0 - validationPercent));// Training
		dataDivisionList.add(new DataDivision(validationPercent));// Validation
		this.dataset.divide(dataDivisionList, shuffle,
				new MersenneTwisterGenerateRandom(seed));
		this.trainingDataset = dataDivisionList.get(0).getDataset();
		this.validationDataset = dataDivisionList.get(1).getDataset();
	}

	public void fitFold(int k, int foldNum, DataFold fold) {
		MLMethod method = this.createMethod();
		MLTrain train = this.createTrainer(method, fold.getTraining());

		if (train.getImplementationType() == TrainingImplementationType.Iterative) {
			SimpleEarlyStoppingStrategy earlyStop = new SimpleEarlyStoppingStrategy(
					fold.getValidation());
			train.addStrategy(earlyStop);

			StringBuilder line = new StringBuilder();
			while (!train.isTrainingDone()) {
				train.iteration();
				line.setLength(0);
				line.append("Fold #");
				line.append(foldNum);
				line.append("/");
				line.append(k);
				line.append(": Iteration #");
				line.append(train.getIteration());
				line.append(", Training Error: ");
				line.append(Format.formatDouble(train.getError(), 8));
				line.append(", Validation Error: ");
				line.append(Format.formatDouble(earlyStop.getValidationError(),
						8));
				report.report(k, foldNum, line.toString());
			}
			fold.setScore(earlyStop.getValidationError());
			fold.setMethod(method);
		} else if (train.getImplementationType() == TrainingImplementationType.OnePass) {
			train.iteration();
			double validationError = calculateError(method,
					fold.getValidation());
			this.report.report(k, k,
					"Trained, Training Error: " + train.getError()
							+ ", Validatoin Error: " + validationError);
			fold.setScore(validationError);
			fold.setMethod(method);
		} else {
			throw new EncogError("Unsupported training type for EncogModel: "
					+ train.getImplementationType());
		}
	}

	public double calculateError(MLMethod method, MLDataSet data) {
		if (this.dataset.getNormHelper().getOutputColumns().size() == 1) {
			ColumnDefinition cd = this.dataset.getNormHelper()
					.getOutputColumns().get(0);
			if (cd.getDataType() == ColumnType.nominal) {
				return EncogUtility.calculateClassificationError(
						(MLClassification) method, data);
			}
		}

		return EncogUtility.calculateRegressionError((MLRegression) method,
				data);
	}

	private MLTrain createTrainer(MLMethod method, MLDataSet dataset) {

		if (this.trainingType == null) {
			throw new EncogError(
					"Please call selectTraining first to choose how to train.");
		}
		MLTrainFactory trainFactory = new MLTrainFactory();
		MLTrain train = trainFactory.create(method, dataset, this.trainingType,
				this.trainingArgs);
		return train;
	}

	public MLMethod crossvalidate(int k, boolean shuffle) {
		KFoldCrossvalidation cross = new KFoldCrossvalidation(
				this.trainingDataset, k);
		cross.process(shuffle);

		int foldNumber = 0;
		for (DataFold fold : cross.getFolds()) {
			foldNumber++;
			report.report(k, foldNumber, "Fold #" + foldNumber);
			fitFold(k, foldNumber, fold);
		}

		double sum = 0;
		double bestScore = Double.POSITIVE_INFINITY;
		MLMethod bestMethod = null;
		for (DataFold fold : cross.getFolds()) {
			sum += fold.getScore();
			if (fold.getScore() < bestScore) {
				bestScore = fold.getScore();
				bestMethod = fold.getMethod();
			}
		}
		sum = sum / cross.getFolds().size();
		report.report(k, k, "Cross-validated score:" + sum);
		return bestMethod;
	}

	/**
	 * @return the trainingDataset
	 */
	public MatrixMLDataSet getTrainingDataset() {
		return trainingDataset;
	}

	/**
	 * @param trainingDataset
	 *            the trainingDataset to set
	 */
	public void setTrainingDataset(MatrixMLDataSet trainingDataset) {
		this.trainingDataset = trainingDataset;
	}

	/**
	 * @return the validationDataset
	 */
	public MatrixMLDataSet getValidationDataset() {
		return validationDataset;
	}

	/**
	 * @param validationDataset
	 *            the validationDataset to set
	 */
	public void setValidationDataset(MatrixMLDataSet validationDataset) {
		this.validationDataset = validationDataset;
	}

	public void selectMethod(VersatileMLDataSet dataset, String methodType,
			String methodArgs, String trainingType, String trainingArgs) {

		if (!this.methodConfigurations.containsKey(methodType)) {
			throw new EncogError("Don't know how to autoconfig method: "
					+ methodType);
		}
		this.methodType = methodType;
		this.methodArgs = methodArgs;
		dataset.getNormHelper().setStrategy(
				this.methodConfigurations.get(methodType)
						.suggestNormalizationStrategy(dataset, methodArgs));

	}

	public MLMethod createMethod() {
		if (this.methodType == null) {
			throw new EncogError(
					"Please call selectMethod first to choose what type of method you wish to use.");
		}
		MLMethodFactory methodFactory = new MLMethodFactory();
		MLMethod method = methodFactory.create(methodType, methodArgs, dataset
				.getNormHelper().calculateNormalizedInputCount(), this.config
				.determineOutputCount(dataset));
		return method;
	}

	public void selectMethod(VersatileMLDataSet dataset, String methodType) {
		if (!this.methodConfigurations.containsKey(methodType)) {
			throw new EncogError("Don't know how to autoconfig method: "
					+ methodType);
		}

		this.config = this.methodConfigurations.get(methodType);
		this.methodType = methodType;
		this.methodArgs = this.config.suggestModelArchitecture(dataset);
		dataset.getNormHelper().setStrategy(
				this.config.suggestNormalizationStrategy(dataset, methodArgs));

	}

	public void selectTrainingType(VersatileMLDataSet dataset) {
		if (this.methodType == null) {
			throw new EncogError(
					"Please select your training method, before your training type.");
		}
		MethodConfig config = this.methodConfigurations.get(methodType);
		selectTraining(dataset, config.suggestTrainingType(),
				config.suggestTrainingArgs(trainingType));
	}

	public void selectTraining(VersatileMLDataSet dataset, String trainingType,
			String trainingArgs) {
		if (this.methodType == null) {
			throw new EncogError(
					"Please select your training method, before your training type.");
		}

		this.trainingType = trainingType;
		this.trainingArgs = trainingArgs;
	}

	/**
	 * @return the methodConfigurations
	 */
	public Map<String, MethodConfig> getMethodConfigurations() {
		return methodConfigurations;
	}

	/**
	 * @return the report
	 */
	public StatusReportable getReport() {
		return report;
	}

	/**
	 * @param report
	 *            the report to set
	 */
	public void setReport(StatusReportable report) {
		this.report = report;
	}
}
