import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import LabelEncoder
from joblib import dump
import os



##################################
## Create DataFrame

def df_wrangling(path_df):
    df = pd.read_csv(path_df, header=None) # Load the CSV file into a DataFrame
    df = df.transpose() # Transpose the DataFrame
    first_row = df.iloc[0,] #take first row, i.w. the name of the columns
    df = df.drop(df.index[0]) #remove first column
    df = df.rename(columns=dict(zip(df.columns, first_row.values))) # Name the columns
    return df


# Specify the directory path
folder_path = "training_data_metaphase/"

# Get a list of all files in the directory
all_files = os.listdir(folder_path)

# Filter files that start with "data_prefiltered" and end with ".csv"
filtered_files = [file for file in all_files if file.startswith('data_prefiltered') and file.endswith('.csv')]

# Create an empty list to store DataFrames
dfs = []

i = 1
# Read each CSV file into a DataFrame and append to the list
for file in filtered_files:
    if(i == 5): break
    file_path = os.path.join(folder_path, file)
    df = df_wrangling(file_path)
    dfs.append(df)
    i+=1


# Merge all DataFrames in the list
merged_df = pd.concat(dfs,axis = 0, ignore_index=True)


##################################
## Train RandomForest

# Create feature Matrix and response vectror
X = df.drop(columns=['label', "idx"])  # Features
y = df['label']  # Target variable
label_encoder = LabelEncoder() #make labels categorical
y = label_encoder.fit_transform(y)

# Initialise RandomForest
rf_clf = RandomForestClassifier(n_estimators=100, random_state=42)

# Fit the Model
rf_clf.fit(X, y)

##################################
## Save Fitted Model

# Save the trained model
dump(rf_clf, 'training_data_metaphase/rf_model.joblib')


