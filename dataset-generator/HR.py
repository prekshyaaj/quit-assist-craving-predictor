import numpy as np

def gym_hr_profile(location):
    """
    Elevates Heart Rate delta significantly during gym workouts.
    """
    if str(location).lower() == 'gym':
        return 40.0
    return 0.0

def sleep_hr_profile(is_sleeping):
    """
    Lowers Heart Rate delta significantly during sleep.
    """
    if is_sleeping == 1:
        return -15.0
    return 0.0

def smoke_hr_profile(tsls_series, smoke_series):
    """
    Calculates Heart Rate Delta for smoking events based on TSLS and a craving precursor.
    Returns a numpy array representing the delta.
    """
    # Recovery curve (minutes since smoking -> HR delta), interpolated so it
    # works for any step length; 0 after 50 minutes
    curve_minutes = [0, 10, 20, 30, 40, 50]
    curve_delta = [10.2, 7.9, 5.3, 4.2, 2.5, 1.0]
    hr_delta = np.interp(tsls_series, curve_minutes, curve_delta, right=0.0)

    # Calculate craving precursor (the step before a cigarette)
    next_smoked = smoke_series.shift(-1).fillna(0)
    is_craving = (next_smoked == 1) & (smoke_series == 0)
    
    hr_delta = np.where(is_craving, 3.5, hr_delta)
    
    return hr_delta

def sensor_noise_profile(location_series, sleep_series, rng):
    """
    Simulates smartwatch PPG sensor noise (quantization noise and motion artifacts).
    Smartwatch HR sensors (PPG) are highly sensitive to motion, meaning noise variance
    increases significantly during activity and decreases during stillness.
    """
    n_len = len(location_series)

    # Base white noise for everyday activities (approx 1.0 bpm standard deviation)
    noise = rng.normal(0, 1.0, n_len)
    # Heavy motion artifacts during gym workouts (higher variance, e.g. 3.5 bpm std dev)
    gym_noise = rng.normal(0, 3.5, n_len)
    # Minimal noise during sleep (very still, e.g. 0.3 bpm std dev)
    sleep_noise = rng.normal(0, 0.3, n_len)

    noise = np.where(location_series.str.lower() == 'gym', gym_noise, noise)
    noise = np.where(sleep_series == 1, sleep_noise, noise)
    
    return noise
