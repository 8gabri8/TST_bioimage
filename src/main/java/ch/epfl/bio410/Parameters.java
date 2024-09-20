package ch.epfl.bio410;

/**
 * This class is used to store all the user input parameters in one structure.
 */
public class Parameters {

        // Sigma value for the Difference of Gaussians (DoG) filter
        double sigma_dog_filter = 2;

        // Stardist Parameters
        // Overlap threshold for Stardist, default value is 0.25
        double overlap_SD = 0.25;

        // Probability threshold for Stardist, default value is 0.5
        double probability_SD = 0.5;

        // Initial filtering parameters for detected ROIs by Stardist
        // Circularity threshold for initial filtering
        double circularity_threshold = 0.9;

        // Minimum area threshold for initial filtering
        double area_min_thr = 10;

        // Maximum area threshold for initial filtering
        double area_max_thr = 80;

        // Margin value for initial filtering.
        double margin = 30;

        // Noise standard deviation threshold for initial filtering
        double noise_std_thr = 300;

        // Data path
        // Directory path where data is stored
        String dataDir = "";

        // Result path
        // Directory path where results will be stored
        String resultsDir = "";

        // Path to the Python environment directory
        String pythonEnvDir = "";

        // Temporary path to store intermediate data
        String tempPath = "";

        // Help link URL
        String helpLink = "https://gitlab.epfl.ch/dcorrea/project-tst";
}
