package ch.epfl.bio410;

//import org.python.antlr.ast.Str;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * This class represents a Well. It stores its letter, name, path, and a list of WellEntries.
 */
public class Well {
    // Letter representing the well
    char wellLetter;

    // Name of the well
    String name;

    // Path to the well directory
    String wellPath;

    // Map of WellEntries, keyed by a unique identifier
    Map<String, WellEntry> wellEntries;

    // List of unmatched entry keys (entries missing data)
    List<String> unmatchedEntries;

    // List of results (WellEntry objects)
    ArrayList<WellEntry> results = new ArrayList<>();

    /**
     * Constructor for the Well class.
     *
     * @param wellName Name of the well
     */
    Well(String wellName){
        if (wellName != null && !wellName.isEmpty()) {
            // Extract the first character of the well name
            this.wellLetter = wellName.charAt(0);
            this.name = wellName;
            this.wellEntries = new HashMap<>();
            this.unmatchedEntries = new ArrayList<>();
        }
    }

    /**
     * Gets the letter representing the well.
     *
     * @return The well letter
     */
    char getWellLetter(){
        return this.wellLetter;
    }

    /**
     * This method goes over all the files in the well and creates entries to process.
     *
     * @return true if all entries are valid, false otherwise
     */
    public boolean setWellEntries() {
        // Regex pattern to match the file naming convention
        String regexPattern = "([A-Z]) - (\\d+)\\(fld (\\d+) wv (\\w+) - \\w+\\)";
        Pattern pattern = Pattern.compile(regexPattern);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(wellPath))) {
            for (Path imagePath : stream) {
                String imageName = imagePath.getFileName().toString();
                Matcher matcher = pattern.matcher(imageName);
                if (matcher.find()) {
                    String wellName = matcher.group(1);
                    String wellNumber = matcher.group(2);
                    String fieldOfView = matcher.group(3);
                    String channel = matcher.group(4);

                    String key = wellName + "_" + wellNumber + "_" + fieldOfView;

                    // Check existing entry
                    WellEntry wellEntry = wellEntries.getOrDefault(key, new WellEntry(key,Integer.parseInt(fieldOfView),null, null));
                    if ("YFP".equalsIgnoreCase(channel)) {
                        wellEntry.setYellowChannelPath(imagePath.toString());
                    } else if ("TexasRed".equalsIgnoreCase(channel)) {
                        wellEntry.setRedChannelPath(imagePath.toString());
                    }
                    wellEntries.put(key, wellEntry);

                    System.out.println("Processed Image: " + imageName);
                }
            }

            // Validate all entries have both channels
            for (Map.Entry<String, WellEntry> entry : wellEntries.entrySet()) {
                if (!entry.getValue().isValid()) {
                    System.err.println("Error: Missing channel data for " + entry.getKey());
                    unmatchedEntries.add(entry.getKey());
                    return false;
                }
            }

            for(String we : unmatchedEntries){
                wellEntries.remove(we);
            }

        } catch (IOException e) {
            System.err.println("IOException encountered: " + e.getMessage());
            return false;
        }
        return true;
    }

}




