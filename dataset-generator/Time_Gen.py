import pandas as pd
# pyrefly: ignore [missing-import]
import numpy as np
import os

# ── Configurable constants ────────────────────────────────────────────────────
STEPS_PER_DAY = 144          # 24 hours × 6 steps/hour (one step = 10 min)
DAYS_PER_TASK = 3            # 2 training days + 1 query day
STEP_MINUTES = 24 * 60 / STEPS_PER_DAY   # length of one step; everything else derives from this
# ─────────────────────────────────────────────────────────────────────────────


def expand_dataset(
    input_path="dataset_prepro.csv",
    output_path="dataset_expanded.csv",
    days_per_task=DAYS_PER_TASK,
    verbose=False,
):
    """
    Expands each row of the input dataset into STEPS_PER_DAY * days_per_task
    rows and generates a time column with one row per STEP_MINUTES.
    verbose=True also prints sample rows of the result.
    """

    if not os.path.exists(input_path):
        raise FileNotFoundError(f"{input_path} not found.")

    print(f"Reading {input_path}...")
    df = pd.read_csv(input_path)
    print(f"Original shape: {df.shape}")

    # 1. Give each row a unique ID as "task_id" (1-indexed)
    df["task_id"] = range(1, len(df) + 1)

    # 2. Duplicate each row STEPS_PER_DAY * days_per_task times
    total_steps = STEPS_PER_DAY * days_per_task
    print(f"Expanding dataset (duplicating each row {total_steps} times)...")
    df_expanded = df.loc[np.repeat(
        df.index.values, total_steps)].reset_index(drop=True)

    # 3. Create time column using % 24 logic, one step = STEP_MINUTES
    # (10 minutes = 10 / 60 hours = 1 / 6 hours)
    print("Generating time column...")
    time_sequence = [(i * STEP_MINUTES / 60) % 24.0 for i in range(total_steps)]
    df_expanded["time"] = time_sequence * len(df)

    # Reorder columns to place task_id and time at the front
    cols = ["task_id", "time"] + [
        col for col in df_expanded.columns
        if col not in ["task_id", "time"]
    ]
    df_expanded = df_expanded[cols]

    print(f"Expanded shape: {df_expanded.shape}")

    if output_path is not None:
        print(f"Saving to {output_path}...")
        df_expanded.to_csv(output_path, index=False)
        print("Saved successfully!")

    if verbose:
        # Print sample of the first task
        print("\nFirst 10 rows of the expanded dataset:")
        print(df_expanded.head(10))

        print(f"\nRows around the first day wrap-around boundary (index {STEPS_PER_DAY - 4} to {STEPS_PER_DAY + 4}):")
        print(df_expanded.iloc[STEPS_PER_DAY - 4 : STEPS_PER_DAY + 4])

    return df_expanded
