import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import sys
import os

def main():

    df_path = sys.argv[1] #path of df with final counts
    image_path = sys.argv[2]  #folder where to save the images

    df = pd.read_csv(df_path)

    # Remove Images with not normal comment
    df = df[df["Comment"] == "Normal"]

    # Control
    if(len(df) == 0):
        print("No Usable Wells remained")
        #sys.exit(10)
        exit(10)

    # Take only Total columns
    df = df[["Well Name", "Total Enriched", "Total Depleted", "Total Intermediate", "Total Nuclei", "Total Nuclei Metaphase"]]

    # Sum rows of the same Well
    df_sum = df.groupby('Well Name').sum()

    # Columns to be divided by "total Nuclei"
    columns_to_divide = ["Total Enriched", "Total Depleted", "Total Intermediate"]
    df_fractions = df_sum.copy() # ATTENTION COPY HARD LINK
    df_fractions[columns_to_divide] = df_sum[columns_to_divide].div(df_sum['Total Nuclei'], axis=0)
    df_fractions.fillna(0, inplace=True) # Replace NA with 0
    df_fractions.rename(columns={"Total Enriched" : "Fraction Enriched", "Total Depleted" : "Fraction Depleted", "Total Intermediate": "Fraction Intermediate"}, inplace=True)

    # print(df_fractions)
    # print(df_sum)

    ###########
    ## PLOTTING RAW COUNTS
    ###########

    df = df_sum

    # Number of rows for subplots
    num_rows = len(df)

    # Create subplots
    fig, axes = plt.subplots(nrows=3, ncols=1, figsize=(15, 15))

    # Loop through each column and create a bar plot
    for i, col in enumerate(columns_to_divide):
        ax = axes[i]
        ax.bar(np.arange(num_rows), df[col], align='center', alpha=0.7, color='red')
        ax.set_title(f'{col} by Well Name')
        ax.set_xlabel('Well Name')
        ax.set_ylabel(col)
        ax.set_xticks(np.arange(num_rows))
        ax.set_xticklabels(df.index, rotation=45, ha='right')

    # Adjust layout
    plt.tight_layout()

    # Show the plot
    #plt.show()

    fig.savefig(os.path.join(image_path, "hist_wells_raw_counts.png")) 

    ###########
    ## PLOTTING RACTIONs
    ###########

    df = df_fractions
    columns_to_divide = ["Fraction Enriched", "Fraction Depleted", "Fraction Intermediate"]

    # Number of rows for subplots
    num_rows = len(df)

    # Create subplots
    fig, axes = plt.subplots(nrows=3, ncols=1, figsize=(15, 15))

    # Loop through each column and create a bar plot
    for i, col in enumerate(columns_to_divide):
        ax = axes[i]
        ax.bar(np.arange(num_rows), df[col], align='center', alpha=0.7)
        ax.set_title(f'{col} by Well Name')
        ax.set_xlabel('Well Name')
        ax.set_ylabel(col)
        ax.set_xticks(np.arange(num_rows))
        ax.set_xticklabels(df.index, rotation=45, ha='right')

    # Adjust layout
    plt.tight_layout()

    # Show the plot
    #plt.show()

    fig.savefig(os.path.join(image_path, "hist_wells_fractions.png")) 

if __name__ == "__main__":
    main()


"""
for testing:
/home/gabri/mambaforge/envs/TF_VE/bin/python3.9 /home/gabri/Desktop/BIOIMAGE/final_project/project-tst/src/training_data_metaphase/plots.py /home/gabri/results_tf/output.csv /home/gabri/results_tf
"""