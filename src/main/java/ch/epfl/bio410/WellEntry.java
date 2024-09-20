package ch.epfl.bio410;

/**
 * This class represents a Well entry.
 * It stores its name, path to the red and yellow channels,
 * and all the computed statistics from our analysis pipeline.
 */
public class WellEntry {

    // Name of the well
    String name;

    // Field of view number
    int fov;

    // Path to the red channel image
    String redChannelPath;

    // Path to the yellow channel image
    String yellowChannelPath;

    // Ratio of enriched cells to nuclei
    float enriched_to_nuclei;

    // Total number of enriched cells
    int totalEnriched = 0;

    // Ratio of depleted cells to nuclei
    float depleted_to_nuclei;

    // Total number of depleted cells
    int totalDepleted = 0;

    // Ratio of intermediate cells to nuclei
    float intermediate_to_nuclei;

    // Total number of intermediate cells
    int totalInter = 0;

    // Total number of nuclei
    float total_nuclei;

    // Total number of nuclei in metaphase
    float total_nuclei_metaphase;

    // Comment on the entry
    EntryComments comment = EntryComments.NORMAL;


    /**
     * Constructor for WellEntry.
     *
     * @param name         Name of the well
     * @param fov          Field of view number
     * @param redPath      Path to the red channel image
     * @param yellowPath   Path to the yellow channel image
     */

    public WellEntry(String name, int fov, String redPath, String yellowPath) {
        this.name = name;
        this.fov = fov;
        this.redChannelPath = redPath;
        this.yellowChannelPath = yellowPath;
    }

    /**
     * Sets the path to the red channel image.
     *
     * @param path Path to the red channel image
     */
    public void setRedChannelPath(String path) {
        this.redChannelPath = path;
    }

    /**
     * Sets the path to the yellow channel image.
     *
     * @param path Path to the yellow channel image
     */
    public void setYellowChannelPath(String path) {
        this.yellowChannelPath = path;
    }

    /**
     * Validates if both red and yellow channel paths are set.
     *
     * @return true if both paths are not null, false otherwise
     */
    public boolean isValid() {
        return redChannelPath != null && yellowChannelPath != null;
    }

    /**
     * Gets the comment associated with this well entry.
     *
     * @return A string describing the comment
     */
    public String getComment() {
        String result = "Normal";
        switch (this.comment) {
            case NOISY: //like facotr/number coded in java
                result = "The red or yellow channel of this entry was too noisy OR empty";
                break;
            case NO_MITOSIS:
                result = "No mitosis was detected in this sample";
                break;
            case EMPTY:
                result = "Image has no cells";
                break;

            default:
                break;
        }
        return result;
    }

    /**
     * Enum representing possible comments on the well entry state.
     * In our data set, we encountered the following cases of wells.
     */
    public enum EntryComments {
        NORMAL, NOISY, NO_MITOSIS, EMPTY
    }
}
