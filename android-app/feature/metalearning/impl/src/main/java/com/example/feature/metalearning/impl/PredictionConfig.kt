package com.example.feature.metalearning.impl

/**
 * Prediction server (MAML + FcNet), deployed from few_shot_meta_learning branch MyEdits3 (deploy/server.py).
 * Try requests in a browser at BASE_URL/docs.
 */
internal object PredictionConfig {
    const val BASE_URL = "https://predict-server.proudcliff-0eaff5e4.centralindia.azurecontainerapps.io"

    /** Sent as X-API-Key if not empty. The deployed server currently doesn't require one. */
    const val API_KEY = ""

    /** Resting HR used until Adapt has returned the user's own value. */
    const val DEFAULT_RESTING_HR = 70.0
}
