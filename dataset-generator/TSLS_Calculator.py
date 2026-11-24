import pandas as pd
import numpy as np

from Time_Gen import STEP_MINUTES

def calculate_tsls(series):
    """
    Calculates Time Since Last Smoked (TSLS) in minutes; each row is one
    STEP_MINUTES step.
    Uses a double-stack concatenation method to seamlessly handle the cyclic
    wrap-around boundary for the first few rows (e.g. infinite 3-day loop).
    """
    # Double the series to simulate perfect cyclic wrap-around
    double_s = pd.concat([series, series]).reset_index(drop=True)
    tsls = np.zeros(len(double_s))
    
    current_tsls = 0
    for i, smoked in enumerate(double_s):
        if smoked == 1:
            current_tsls = 0
        else:
            current_tsls += STEP_MINUTES
        tsls[i] = current_tsls
        
    # Return only the second half which now has perfect wrap-around history
    return tsls[len(series):]
