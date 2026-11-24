BASE_VISIT_PROB = {
    "gym": 0.234,
    "restaurant": 0.969,
    "market": 0.397,
    "entertainment": 0.304,
    "public_place": 0.304,
    "hospital": 0.000957
}

SEX_MULTIPLIERS = {
    "gym": {
        1: 0.257 / 0.234,
        2: 0.211 / 0.234,
    },
    "restaurant": {
        1: 0.972 / 0.969,
        2: 0.965 / 0.969,
    },
    "market": {
        1: 0.366 / 0.397,
        2: 0.426 / 0.397,
    },
    "entertainment": {
        1: 0.282 / 0.304,
        2: 0.325 / 0.304,
    },
    "public_place": {
        1: 0.282 / 0.304,
        2: 0.325 / 0.304,
    },
}

AGE_MULTIPLIERS = {
    "gym": {
        "all_ages": 1.00,
    },
    "restaurant": {
        "all_ages": 1.00,
    },
    "market": {
        "all_ages": 1.00,
    },
    "entertainment": {
        "all_ages": 1.00,
    },
    "public_place": {
        "all_ages": 1.00,
    },
}

HOSPITAL_PROB_BY_SEX = {
    1: 0.000682,
    2: 0.001219,
}

HOSPITAL_PROB_BY_AGE = {
    "18-44": 0.000890,
    "45-54": 0.001091,
    "55-64": 0.001145,
    "65+":   0.000866,
}

def get_age_group(age: float) -> str:
    if age < 45:
        return "18-44"
    elif age < 55:
        return "45-54"
    elif age < 65:
        return "55-64"
    else:
        return "65+"

def calculate_user_probabilities(age: float, sex: int) -> dict:
    """
    Returns a dictionary of unstructured activity probabilities for a user.
    sex: 1 = Male, 2 = Female
    """
    probs = {}
    
    # Standard unstructured activities
    activities = ["gym", "restaurant", "market", "entertainment", "public_place"]
    for act in activities:
        base = BASE_VISIT_PROB[act]
        sex_mult = SEX_MULTIPLIERS.get(act, {}).get(sex, 1.0)
        # AGE_MULTIPLIERS defaults to 1.0 for all ages per the rules
        age_mult = 1.0
        
        probs[act] = base * sex_mult * age_mult
        
    # Hospital logic (CDC data)
    age_group = get_age_group(age)
    hospital_age_prob = HOSPITAL_PROB_BY_AGE.get(age_group, BASE_VISIT_PROB["hospital"])
    hospital_sex_prob = HOSPITAL_PROB_BY_SEX.get(sex, BASE_VISIT_PROB["hospital"])
    
    # We can average them or take base * (age_prob / base) * (sex_prob / base)
    # The rules mention "if you want one simple baseline, use overall value".
    # Here we'll use a combined approach if possible, or just the sex multiplier.
    base_hosp = BASE_VISIT_PROB["hospital"]
    if base_hosp > 0:
        age_mult = hospital_age_prob / base_hosp
        sex_mult = hospital_sex_prob / base_hosp
        probs["hospital"] = base_hosp * sex_mult * age_mult
    else:
        probs["hospital"] = 0.0
        
    return probs
