package ch.epfl.bio410;

import de.csbdresden.stardist.StarDist2D;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import java.awt.*;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;




@Plugin(type = Command.class, menuPath = "Plugins>TF Analyze") //"Plugins>BII>TF Analyzer"

public class ProjectCommand implements Command {

	// member variable used to store all the user inputs from the GUI
	Parameters params = new Parameters();
	// member variable that represents an array of Wells
	Results results = new Results();
	// a map used to analyze and output results of the wells in alphabetical order
	Map<Character, List<Well>> wellsByInitial = new HashMap<>();

	/**
	 * Main method to run the analysis pipeline.
	 */
	public void run() {
		// get user input, all stored in our Parameters class
		if (!getUserInputParameters())
			return;

		// this parses the whole data folder and creates Wells with WellEntries.
		// Each WellEntry corresponds to an image pair of red and yellow channel and has it's computed statistics.
		parseDataFolder();

		// run our analysis pipeline on all the wells
		evaluateAllWells();

		// write out csv with analysis results
		printFullDataMetrics();

		// use the output csv and create some plots
		savePlots();

		// clean up temp folder
		File folder = new File(params.tempPath);
		if(!deleteFolder(folder)){
			System.out.println("Unable to delete temp folder with intermediate data.");
		}

	}

	/**
	 * Show GUI to user with all tunable parameters, handling for no directory fields provided.
	 *
	 * @return true if user input parameters are obtained successfully, false otherwise
	 */
	public boolean getUserInputParameters(){
		// CREATING CUSTOM GUI
		GenericDialog gd = new GenericDialog("Transcription Factors - Parameters");

		gd.addMessage("Preprocessing parameters: ");
		gd.addNumericField("Gaussian Filter sigma", params.sigma_dog_filter, 2);
		gd.addMessage("Stardist parameters for cell segmentation: ");
		gd.addNumericField("Overlap", params.overlap_SD, 2);
		gd.addNumericField("Probability", params.probability_SD, 2);
		gd.addMessage("Initial filtering parameters: ");
		gd.addNumericField("Noise std deviation Threshold", params.noise_std_thr, 2);
		gd.addNumericField("Circularity Threshold", params.circularity_threshold, 2);
		gd.addNumericField("Minimum cell area", params.area_min_thr, 2);
		gd.addNumericField("Maximum cell area", params.area_max_thr, 2);
		gd.addNumericField("Margin for SVM", params.margin, 2);
		gd.addDirectoryField("Select the folder that contains data to be analyzed: ", params.dataDir);
		gd.addDirectoryField("Select the output folder: ", params.resultsDir);
		gd.addDirectoryField("Path to your python environment:", params.pythonEnvDir);
		gd.addMessage("Click Help Button for README and parameters explanation");
		gd.addHelp(params.helpLink);
		gd.showDialog();

		if (gd.wasCanceled()) return false;
		params.sigma_dog_filter = gd.getNextNumber();
		params.overlap_SD = gd.getNextNumber();
		params.probability_SD = gd.getNextNumber();
		params.noise_std_thr = gd.getNextNumber();
		params.circularity_threshold = gd.getNextNumber();
		params.area_min_thr = gd.getNextNumber();
		params.area_max_thr = gd.getNextNumber();
		params.margin = gd.getNextNumber();
		params.dataDir = gd.getNextString();
		params.resultsDir = gd.getNextString();
		params.pythonEnvDir = gd.getNextString();
		//set temp path here
		params.tempPath = params.resultsDir + "temp";

		if (gd.wasOKed()){
			// if data and result directories are empty, show it again until canceled or paths are provided
			while (params.resultsDir.isEmpty() || params.dataDir.isEmpty() || params.pythonEnvDir.isEmpty()) {
				gd.addMessage("Data or output directory is missing!");
				gd.showDialog();
				if (gd.wasCanceled()) return false;
			}
		}

		return true;
	}

	/**
	 * This method goes through the data folder provided and creates Wells with its WellEntries.
	 *
	 * @return true if parsing is successful, false otherwise
	 */
	public boolean parseDataFolder(){
		Path dirPath = Paths.get(params.dataDir);

		// Using try-with-resources to ensure that the stream is closed after use
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
			for (Path entry : stream) {
				if (Files.isDirectory(entry)) {
					String wellName = String.valueOf(entry.getFileName());
					Well currWell = new Well(wellName);
					results.wells.add(currWell); // save it to list of results
					wellsByInitial.computeIfAbsent(currWell.getWellLetter(), k -> new ArrayList<>()).add(currWell); // add them to map organized by well letter
					currWell.wellPath = params.dataDir + wellName;
					System.out.println("Directory Name: " + params.dataDir + entry.getFileName());
					boolean result = currWell.setWellEntries();
					System.out.println("All entries set successfully for well " + entry.getFileName() +": " + result);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return true;
	}

	/**
	 * This method iterates over the Wells in alphabetical order and calls analyzeWell() on each.
	 *
	 * @return true if evaluation is successful, false otherwise
	 */
	public boolean evaluateAllWells() {
		boolean result = true;
		//PROCESS THEM IN ALPHABETICAL ORDER!
		for (Map.Entry<Character, List<Well>> entry : wellsByInitial.entrySet()) {
			for (Well well : entry.getValue()) {
				result = analyzeWell(well);
			}
		}
		return result;
	}

	/**
	 * This method takes in a Well, iterates over all its WellEntries and calls analyzeEntry on them.
	 *
	 * @param well The Well object to be analyzed
	 * @return true if the analysis is successful, false otherwise
	 */
	public boolean analyzeWell(Well well){
		boolean result = true;
		for(WellEntry wellEntry : well.wellEntries.values())
			analyzeEntry(wellEntry);
		return result;

	}

	/**
	 * This method reads in the produced output.csv file, reads the data and outputs two new files with plots: hist_wells_fractions.png and hist_wells_raw_counts.png.
	 *
	 * @return true if the plots are saved successfully, false otherwise
	 */
	public boolean savePlots() {
		// Save the plots after running all the program
		boolean result = true;

		String plots_script_path = Paths.get(params.pythonEnvDir, "classify_metaphase/plots.py").toString();
		String final_csv_path = Paths.get(params.resultsDir, "output.csv").toString();
		String folder_to_save_plots = params.resultsDir;
		String python_exe_path = "";

		String os = System.getProperty("os.name"); //depending on the OS the location of the .exe python has to be adapted
		if(os.charAt(0) == 'W'){
			python_exe_path = Paths.get(params.pythonEnvDir, "python").toString();
		}
		else{
			python_exe_path = Paths.get(params.pythonEnvDir, "bin/python3.9").toString();
		}

		String command = python_exe_path + " " + plots_script_path + " " + final_csv_path + " " + folder_to_save_plots;
		//String[] command = {"python", classify_metaphse_script_path, csv_path};

		ArrayList<Integer> pred_metapahse = new ArrayList<>();

		try {
			Process p = Runtime.getRuntime().exec(command);

			// Capture error output
			BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String error;
			while ((error = stdError.readLine()) != null) {
				System.out.println("Python Error: " + error);
			}
			// Wait for process to complete
			int exitCode = p.waitFor();
			if (exitCode == 0) {
				System.out.println("Python script executed successfully.");
			} else if (exitCode == 10) {
				System.out.println("No Images created due to no usable Wells present in the output.csv file.");
			}
			else {
				System.out.println("Error executing Python script. Exit code: " + exitCode);
			}

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}



		return result;
	}

	/**
	 * This method runs the analysis pipeline on each WellEntry.
	 *
	 * @param entry The WellEntry object to be analyzed
	 * @return true if the analysis was successful, false otherwise
	 */
	public boolean analyzeEntry(WellEntry entry){

		boolean result = true;
		// Settings
		//String base_path = params.resultsDir;
		String image_name = entry.name; //name of the Well
		String results_path = params.resultsDir;
		String temp_path = params.tempPath; //new folder to contain temporary files, csv, ...
		String csv_path = temp_path + "/data_" + image_name + ".csv"; //path of csv file with the feature of each ROI

		// Create temporary folder
		File folder = new File(temp_path);
		if (!folder.exists()) {
			boolean created = folder.mkdir(); // Create the folder
			if (created) {
				System.out.println("Folder created successfully.");
			} else {
				System.out.println("Failed to create the folder.");
			}
		} else {
			System.out.println("Folder already exists.");
		}

		/////////////////////////////////////////////////////////////
		// Open Images

		//Show nucleus
		String nucl_path = entry.redChannelPath;
		ImagePlus nucl = IJ.openImage(nucl_path);
		nucl.setTitle("nucl");
		nucl.show();


		//Show yfp
		String yfp_path = entry.yellowChannelPath;
		ImagePlus yfp = IJ.openImage(yfp_path);
		yfp.setTitle("yfp");
		yfp.show();

		// Duplicate
		ImagePlus nuclDup = nucl.duplicate();
		nuclDup.setTitle("nuclDup");
		nuclDup.show();
		ImagePlus yfpDup = yfp.duplicate();
		yfpDup.setTitle("yfpDup");
		yfpDup.show();

		//Create composite image
		IJ.run(nuclDup, "Enhance Contrast", "saturated=0.35");
		IJ.run(yfpDup, "Enhance Contrast", "saturated=0.35");
		IJ.run("Merge Channels...", "c1=[nuclDup] c7=[yfpDup] create keep");
		ImagePlus composite = WindowManager.getImage("Composite");
		composite.setTitle("Composite");

		//hide
		nuclDup.hide();
		yfpDup.hide();

		/////////////////////////////////////////////////////////////
		// Remove Noisy Images, i.e. the one that have to low std

		IJ.run("Clear Results", "");
		IJ.run(yfp, "Measure", "");

		ResultsTable rt = ResultsTable.getResultsTable();

		double[] std = rt.getColumn("StdDev");

		if (std[0] < params.noise_std_thr ){
			entry.comment = WellEntry.EntryComments.NOISY; //append comment to final results
			IJ.run("Close All", ""); // Close all
			return result; // Exit this Well
		}

		/////////////////////////////////////////////////////////////
		// Preprocessing

		// Blur with gaussian filter
		IJ.run(nuclDup, "Gaussian Blur...", "sigma="+params.sigma_dog_filter);

		// Remove background
		IJ.run(nuclDup,"Subtract Background...", "rolling=50 stack");

		// Adjust contrast
		IJ.run(nuclDup, "Enhance Contrast", "saturated=0.35");
		IJ.run(nuclDup, "Apply LUT", ""); //Change the value of the pixel, Stardist gets better results

		/////////////////////////////////////////////////////////////
		// Segment with Stardist

		//Stardist
		ImageJ imageJ = new ImageJ();

		Dataset dataset = imageJ.convert().convert(new ImgPlus(ImageJFunctions.wrap(nuclDup)), Dataset.class);

		try {
			imageJ.command().run(StarDist2D.class, false, "input", dataset, "modelChoice", "Versatile (fluorescent nuclei)", "normalizeInput", true, "percentileBottom", 1.0, "percentileTop", 100.0, "probThresh", params.probability_SD, "nmsThresh", params.overlap_SD, "outputType", "ROI Manager", "nTiles", 1, "excludeBoundary", 0, "roiPosition", "Automatic", "verbose", false, "showCsbdeepProgress", true, "showProbAndDist", false).get(); //, process=[false]")
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}

		/////////////////////////////////////////////////////////////
		// Remove ROIs that are FOR SURE not in mitosis (less computation)
		//			too big/small
		//			on the edges
		//          not thin enough

		RoiManager rm = RoiManager.getRoiManager();

		rm.save(temp_path + "/RoiSet_stardist_" +  image_name + ".zip"); //Save ALL roi from StarDist

		// get the number of ROIs within the ROI Manager
		int nROI = rm.getCount();  //StarDist has already sent the ROIs here

		//if no ROI are present --> exit
		if (nROI == 0){
			entry.comment = WellEntry.EntryComments.EMPTY; //append comment to the final results
			IJ.run("Close All", "");
			return result;
		}

		entry.total_nuclei = nROI; // total nuclei identified by Stardist

		// set measures to have
		IJ.run("Set Measurements...", "area mean perimeter standard fit shape feret's integrated");

		// Measure
		rm.deselect();
		IJ.run("Clear Results", "");
		rm.runCommand(nucl, "Measure");

		rt = ResultsTable.getResultsTable();

		double[] area = rt.getColumn("Area");
		double[] temp_circ = rt.getColumn("Circ.");

		double circ_thr = params.circularity_threshold;
		double area_min_thr = params.area_min_thr;
		double area_max_thr = params.area_max_thr;
		int width = nucl.getWidth();
		int height = nucl.getHeight();

		for (int j=nROI-1; j>=0; j--) {

			rm.select(j); // select a specific roi
			Roi roi = rm.getRoi(j);

			// Remove ROIs based on area
			if ( area[j] > area_max_thr  || area[j] < area_min_thr ){
				rm.runCommand("Delete");
				continue;
			}

			// Remove ROI based on Circularity
			if ( temp_circ[j] > circ_thr ){
				rm.runCommand("Delete");
				continue;
			}

			// Check if ROI is on the border
			if (roi.getBounds().x <= 0 || roi.getBounds().y <= 0 ||
					roi.getBounds().x + roi.getBounds().width >= width ||
					roi.getBounds().y + roi.getBounds().height >= height) {
				rm.runCommand("Delete");
			}

		}

		/////////////////////////////////////////////////////////////

		nROI = rm.getCount(); // ROI number changes

		//if no ROI exit
		if (nROI == 0){
			entry.comment = WellEntry.EntryComments.NO_MITOSIS;
			IJ.run("Close All", "");
			return result;
		}

		IJ.run(nucl, "From ROI Manager", "Show All with Labels");

		/////////////////////////////////////////////////////////////
		// Save ROis and change name of the ROIS

		// rename ROIs
		for (int j=nROI-1; j>=0; j--) {

			rm.select(j); // select a specific roi
			rm.runCommand("Rename", Integer.toString(j+1));

		}

		rm.deselect(); 		// deselect all the ROIs to be able to make a measurement on all ROIs
		rm.save(temp_path + "/RoiSet_prefiltering_" +  image_name + ".zip"); //Save rois after pre filtering

		/////////////////////////////////////////////////////////////
		// Extract measurement nucl IN

		rm.deselect(); 		// deselect all the ROIs to be able to make a measurement on all ROIs
		IJ.run("Clear Results", "");
		rm.runCommand(nucl, "Measure"); //NB calculate from nucl
		rt = ResultsTable.getResultsTable();

		double[] majors = rt.getColumn("Major");
		double[] minors = rt.getColumn("Minor");
		double[] perimeter = rt.getColumn("Perim.");
		area = rt.getColumn("Area");
		double[] circularity = rt.getColumn("Circ.");		//circularity = 4pi(area/perimeter^2), A circularity value of 1.0 indicates a perfect circle
		double[] AR = rt.getColumn("AR"); 					//Aspect ratio axis- / axis+
		double[] roundness = rt.getColumn("Round");
		double[] solidity = rt.getColumn("Solidity");
		double[] nucl_std_in = rt.getColumn("StdDev");
		double[] nucl_mean_in = rt.getColumn("Mean");

		double[] axis_ratio = new double[nROI];
		double[] frag_ratio = new double[nROI];
		for (int j=nROI-1; j>=0; j--) {
			rm.select(j); // select a specific roi
			axis_ratio[j] =  minors[j] / majors[j];
			frag_ratio[j] = area[j] / perimeter[j];
		}

		/////////////////////////////////////////////////////////////
		// Extract measurement yfp IN

		rm.deselect(); 		// deselect all the ROIs to be able to make a measurement on all ROIs
		IJ.run("Clear Results", "");
		rm.runCommand(yfp, "Measure"); //NB calculate from yfp
		rt = ResultsTable.getResultsTable();

		double[] yfp_std_in = rt.getColumn("StdDev");
		double[] yfp_mean_in = rt.getColumn("Mean");

		/////////////////////////////////////////////////////////////
		// Create external band

		double size_band = 1;

		for (int j=nROI-1; j>=0; j--) {

			rm.select(j); // select a specific roi

			IJ.run("Make Band...", "band=" + size_band);// compute a band around the selected ROI
			rm.runCommand("Update"); // update the selected ROI with the new shape (i.e. band)

		}

		/////////////////////////////////////////////////////////////
		// Extract measurement nucl OUT

		rm.deselect();
		IJ.run("Clear Results", "");
		rm.runCommand(nucl, "Measure"); //NB calculate from nucl
		rt = ResultsTable.getResultsTable();
		double[] nucl_mean_out = rt.getColumn("Mean");
		double[] nucl_std_out = rt.getColumn("StdDev");

		/////////////////////////////////////////////////////////////
		// Extract measurement yfp OUT

		rm.deselect();
		IJ.run("Clear Results", "");
		rm.runCommand(yfp, "Measure"); //NB calculate from nucl
		rt = ResultsTable.getResultsTable();
		double[] yfp_mean_out = rt.getColumn("Mean");
		double[] yfp_std_out = rt.getColumn("StdDev");

		/////////////////////////////////////////////////////////////
		// columns for classification

		double[] idx = new double[nROI];
		for(int j = 0; j < nROI; j++){
			idx[j] = (double) j+1; //Attention the numbers shown by the ROI manager starts from 1
		}
		double[] label = new double[nROI];
		for(int j = 0; j < nROI; j++){
			label[j] = (double) 0;
		}

		/////////////////////////////////////////////////////////////
		// create csv file with all the features of each csv

		double[][] arrays = {idx, label, majors, minors, area, perimeter, circularity, AR, roundness, solidity, nucl_std_in, nucl_std_out, nucl_mean_in, nucl_mean_out};
		String[] arrayNames = {"idx", "label", "majors", "minors", "area", "perimeter", "circularity", "AR", "roundness", "solidity", "nucl_std_in", "nucl_std_out", "nucl_mean_in", "nucl_mean_out"};

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(csv_path))) {
			for (int i = 0; i < arrays.length; i++) {
				writer.write(arrayNames[i]);
				for (int j = 0; j < arrays[i].length; j++) {
					writer.write("," + arrays[i][j]);
				}
				writer.newLine();
			}

			System.out.println("CSV file written successfully.");
		} catch (IOException e) {
			System.err.println("Error writing CSV file: " + e.getMessage());
		}

		/////////////////////////////////////////////////////////////
		// remove unwanted ROIs with random forest --> only the one that represent ROI in Metaphase

		String classify_metaphse_script_path = Paths.get(params.pythonEnvDir, "classify_metaphase/classify_metaphase_random_forest.py").toString();
		String rf_path = Paths.get(params.pythonEnvDir, "classify_metaphase/rf_model.joblib").toString(); //path to the random forest file
		String python_exe_path = "";

		//depending on the OS the location of the .exe python has to be adapted
		String os = System.getProperty("os.name");
		if(os.charAt(0) == 'W'){
			python_exe_path = Paths.get(params.pythonEnvDir, "python").toString();
		}
		else{
			python_exe_path = Paths.get(params.pythonEnvDir, "bin/python3.9").toString();
		}

		String command = python_exe_path + " " + classify_metaphse_script_path + " " + csv_path + " " + rf_path;
		//String[] command = {"python", classify_metaphase_script_path, csv_path};

		ArrayList<Integer> pred_metaphase = new ArrayList<>();

		try {
			Process p = Runtime.getRuntime().exec(command); //run the command

			// Option 1: Read output directly using InputStreamReader
			InputStreamReader reader = new InputStreamReader(p.getInputStream());
			BufferedReader buffer = new BufferedReader(reader);
			String line;
			while ((line = buffer.readLine()) != null) {
				pred_metaphase.add(Integer.parseInt(line));
				//IJ.log(line);
			}
			buffer.close();
			reader.close();

			// Capture error output
			BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String error;
			while ((error = stdError.readLine()) != null) {
				System.out.println("Python Error: " + error);
			}
			// Wait for process to complete
			int exitCode = p.waitFor();
			if (exitCode == 0) {
				System.out.println("Python script executed successfully.");
			} else {
				System.out.println("Error executing Python script. Exit code: " + exitCode);
			}

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		if (pred_metaphase.size() != nROI) {
			System.out.println("problem, nROI is not equal to the result of the random forest");
			System.out.println("WE HAVE PROBLEMS PRED_META" + pred_metaphase.size() + " nRoi: " + nROI);

			entry.comment = WellEntry.EntryComments.EMPTY;
			IJ.run("Close All", "");
			return result;
		}

		for (int j=nROI-1; j>=0; j--) {

			rm.select(j); // select a specific roi

			if(pred_metaphase.get(j) == 0){ // Remove Rois classified as not in metaphase
				rm.runCommand("Delete");
				continue;
			}
		}

		/////////////////////////////////////////////////////////////

		nROI = rm.getCount();

		//if no ROI exit
		if (nROI == 0){
			entry.comment = WellEntry.EntryComments.NO_MITOSIS;
			IJ.run("Close All", "");
			return result;
		}

		IJ.run(nucl, "From ROI Manager", "Show All with Labels");
		rm.save(results_path + "/RoiSet_final_" +  image_name + ".zip"); //Save final ROis after RF

		entry.total_nuclei_metaphase = nROI; // final number of nuclei in metaphase!

		rm.close();

		/////////////////////////////////////////////////////////////
		// Classification

		int[] types = new int[nROI]; //label array

		//double std_thr = 500;
		double margin = params.margin;

		for (int j=0; j<nROI; j++) {

			double out_in = (yfp_mean_out[j] - yfp_mean_in[j]) ; /// (yfp_out[j] + yfp_in[j]);

			double distance = Math.abs(yfp_mean_in[j] - yfp_mean_out[j] ) / Math.sqrt(2);

			if( distance < margin){ //intermediate
				types[j] = 1;
				entry.totalInter++;
			} else if((distance > margin)  && (out_in > 0)){ //out>in && out!=in enogth (their differnce is big enogth) --> we are in the depleted case
				types[j] = 2;
				entry.totalDepleted++;
			} else if((distance > margin)  && (out_in < 0)){
				types[j] = 0;
				entry.totalEnriched++;
			}

			//IJ.log("index:" + (j+1) + " ,type: " + types[j] + "out_in: " +  out_in + "   dist: " + distance);

		}

		entry.enriched_to_nuclei = entry.totalEnriched / entry.total_nuclei;
		entry.depleted_to_nuclei = entry.totalDepleted / entry.total_nuclei;
		entry.intermediate_to_nuclei = entry.totalInter / entry.total_nuclei;

		Color[] colors = {Color.RED, Color.GREEN, Color.BLUE};

		Plot plot = new Plot("", "yfp_in" , "yfp_out");

		for (int j = 0; j < nROI; j++) {
			int colorIndex = types[j]; // Example color assignment based on third variable
			plot.setColor(colors[colorIndex]);
			plot.addPoints(new double[]{yfp_mean_in[j]}, new double[]{yfp_mean_out[j]}, Plot.CIRCLE);
		}

		// Add line y = x
		double minX = Arrays.stream(yfp_mean_in).min().orElse(0);
		double maxX = Arrays.stream(yfp_mean_in).max().orElse(10);
		plot.setColor(Color.BLACK); // Line color
		plot.addPoints(new double[]{minX, maxX}, new double[]{minX, maxX}, Plot.LINE);

		//change the frame limits
		plot.setAxisXLog(false);
		plot.setAxisYLog(false);
		double delta = 100;
		plot.setLimits(Arrays.stream(yfp_mean_in).min().getAsDouble() - delta,Arrays.stream(yfp_mean_in).max().getAsDouble() + delta,Arrays.stream(yfp_mean_out).min().getAsDouble() - delta,Arrays.stream(yfp_mean_out).max().getAsDouble() - delta);
		plot.update();
		plot.show();

		//printFullDataMetrics();


		//////////////////////////////////////////////////////////
		//Close all images

		IJ.run("Close All", "");
		return result;
	}

	/**
	 * This method iterates over all the Wells in alphabetical order and prints out each WellEntry's computed statistics.
	 */
	void printFullDataMetrics()
	{
		try (PrintWriter writer = new PrintWriter(new FileWriter(params.resultsDir+"output.csv"))) {
			// Write the header line
			writer.println("Well Name,FoV,Enriched to Nuclei Ratio,Total Enriched,Depleted to Nuclei Ratio,Total Depleted,Intermediate to Nuclei Ratio,Total Intermediate,Total Nuclei,Total Nuclei Metaphase,Comment");
			// Iterate over each entry in the map
			for (Map.Entry<Character, List<Well>> entry : wellsByInitial.entrySet()) {
				for (Well well : entry.getValue()) {
					// Assume each well has a list of data entries
					for (WellEntry data : well.wellEntries.values()) {
						String line = String.format("%s,%d,%f,%d,%f,%d,%f,%d,%f,%f,%s",
								well.name,
								data.fov, data.enriched_to_nuclei, data.totalEnriched,
								data.depleted_to_nuclei, data.totalDepleted,
								data.intermediate_to_nuclei, data.totalInter,
								data.total_nuclei, data.total_nuclei_metaphase,
								data.getComment()
								);
						writer.println(line);
					}
				}
			}
		} catch (IOException e) {
			System.err.println("Error writing to CSV file: " + e.getMessage());
		}
	}

	/**
	 * Method to delete a folder and its contents.
	 *
	 * @param folder The folder to be deleted
	 * @return true if the folder was deleted successfully, false otherwise
	 */
	public static boolean deleteFolder(File folder) {
		if (folder.isDirectory()) {
			File[] files = folder.listFiles();
			if (files != null) { // Some JVMs return null for empty directories
				for (File file : files) {
					if (!deleteFolder(file)) {
						return false; // Stop if unable to delete a file
					}
				}
			}
		}
		return folder.delete(); // Delete the directory itself
	}

	/**
	 * This main function serves for development purposes.
	 * It allows you to run the plugin immediately out of
	 * your integrated development environment (IDE).
	 *
	 * @param args whatever, it's ignored
	 * @throws Exception
	 */
	public static void main(final String... args) throws Exception {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
	}
}
