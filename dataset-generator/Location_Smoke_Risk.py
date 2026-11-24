import pandas as pd
import numpy as np

def get_home_smoker_profile(function_assignment_probability=0.500, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "home"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_office_smoker_profile(function_assignment_probability=0.241, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        locs = locations.str.strip().str.lower()
        mask = locs.isin(["office", "factory"])
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_construction_site_smoker_profile(function_assignment_probability=0.468, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "construction site"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_restaurant_smoker_profile(function_assignment_probability=0.394, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "restaurant"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_hospital_smoker_profile(function_assignment_probability=0.157, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "hospital"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_public_place_smoker_profile(function_assignment_probability=0.468, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "public place"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_school_smoker_profile(function_assignment_probability=0.034, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "school"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_gym_smoker_profile(function_assignment_probability=0.040, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "gym"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_market_smoker_profile(function_assignment_probability=0.280, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "market"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile

def get_entertainment_smoker_profile(function_assignment_probability=0.394, *, rng, high=0.400, low=0.015):
    rand_val = rng.random()
    is_applicable = rand_val < function_assignment_probability
    def apply_profile(locations: pd.Series) -> pd.Series:
        mask = locations.str.strip().str.lower() == "entertainment"
        risk = high if is_applicable else low
        return pd.Series(np.where(mask, risk, low), index=locations.index)
    return apply_profile
