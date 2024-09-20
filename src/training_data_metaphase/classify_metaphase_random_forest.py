from joblib import load
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import LabelEncoder
from joblib import dump
import sys


def main():

    df_path = sys.argv[1] #path df with features
    rf_path = sys.argv[2] # path random forest


    # save_csv_pred = False
    # if len(sys.argv) > 2:
    #     precition_path = sys.argv[2] #èath where to save predictions
    #     save_csv_pred = True



    def df_wrangling(path_df):
        df = pd.read_csv(path_df, header=None) # Load the CSV file into a DataFrame
        df = df.transpose() # Transpose the DataFrame
        first_row = df.iloc[0,] #take first row, i.w. the name of the columns
        df = df.drop(df.index[0]) #remove first column
        df = df.rename(columns=dict(zip(df.columns, first_row.values))) # Name the columns
        return df

    # Load the saved model
    #rf_clf = load('training_data_metaphase/rf_model.joblib')
    rf_clf = load(rf_path)

    ##################################
    ## Classify new Dataset

    df = df_wrangling(df_path) #'training_data_metaphase/data_E_1_1_manually_labelled.csv'

    X = df.drop(columns=['label', "idx"])  # Features

    y_pred = rf_clf.predict(X)

    df["label"] = y_pred #overwrite

    df_pred = df[["label"]].copy() #Create df with only one column

    # if save_csv_pred:
    #     df_pred.to_csv(precition_path, index=False) #'temp/data_E_1_1_predicted.csv'

    for i in df_pred["label"].values:
        print(i)

if __name__ == "__main__":
    main()
