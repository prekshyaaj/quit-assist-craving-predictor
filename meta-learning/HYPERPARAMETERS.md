# Tabular Few-Shot Meta-Learning: Comprehensive Hyperparameter Reference

This document provides an exhaustive, authoritative reference for every hyperparameter in the tabular few-shot meta-learning framework. It covers all stages of the pipeline: data preprocessing, neural backbone architectures, meta-training, few-shot fast adaptation, and cold-start zero-shot inference across all five implemented meta-learning algorithms: **MAML**, **PLATIPUS**, **VAMPIRE**, **ABML**, and **BMAML**, as well as both supported model architectures (**`FcNet`** and **`LogisticRegression`**).

---

## Table of Contents
1. [Centralized Configuration (`tabular/config.json`)](#1-centralized-configuration-tabularconfigjson)
2. [Dataset & Preprocessing Hyperparameters (Stage 1)](#2-dataset--preprocessing-hyperparameters-stage-1)
3. [Base Network Architectures (`FcNet` & `LogisticRegression`)](#3-base-network-architectures-fcnet--logisticregression)
4. [Meta-Training Hyperparameters (Stage 2)](#4-meta-training-hyperparameters-stage-2)
5. [Fast Adaptation Evaluation Hyperparameters (Stage 3)](#5-fast-adaptation-evaluation-hyperparameters-stage-3)
6. [Cold-Start / Zero-Shot Evaluation Hyperparameters (Stage 4)](#6-cold-start--zero-shot-evaluation-hyperparameters-stage-4)
7. [Focal Loss Hyperparameters](#7-focal-loss-hyperparameters)
8. [Algorithm-Specific Deep Dive (MAML, PLATIPUS, VAMPIRE, ABML, BMAML)](#8-algorithm-specific-deep-dive)
9. [Algorithm Hyperparameter Applicability Matrix](#9-algorithm-hyperparameter-applicability-matrix)
10. [Hyperparameter Tuning Guide & Best Practices](#10-hyperparameter-tuning-guide--best-practices)
11. [Behaviour Changes vs. Upstream `master`](#11-behaviour-changes-vs-upstream-master)

---

## 1. Centralized Configuration (`tabular/config.json`)

The entire tabular pipeline is driven by `tabular/config.json`. The active production configuration is shown below:

```json
{
  "constant": {
    "steps_per_day": 144,
    "note": "1 day = 144 time steps (24 hours at 10-minute intervals). Ground truth constant."
  },
  "preprocessing": {
    "raw_dir": "tabular/data/raw",
    "output_dir": "tabular/data/processed",
    "primary_csv": "dataset_prepro_routine_generated.csv",
    "seed": 42,
    "train_frac": 0.85,
    "val_frac": 0.15,
    "test_frac": 0,
    "support_days": 1,
    "tsls_cap": 1220
  },
  "fcnet": {
    "num_hidden_units": [
      64,
      64
    ],
    "activation": "leaky_relu",
    "dropout_rate": 0,
    "use_layernorm": false
  },
  "meta_training": {
    "device": "cpu",
    "num_epochs": 100,
    "num_episodes_per_epoch": 1000,
    "minibatch": 5,
    "minibatch_print": 250,
    "validate_every_n_epochs": 1,
    "meta_lr": 0.001,
    "inner_lr": 0.01,
    "num_inner_updates": 5,
    "first_order": true,
    "num_models": 8,
    "KL_weight": 1e-06,
    "classification_threshold": 0.35,
    "svgd_bandwidth_scale": 1,
    "svgd_repulsive_weight": 1,
    "gamma_prior_concentration": 1,
    "gamma_prior_rate": 0.01,
    "normal_prior_loc": 0,
    "normal_prior_scale": 1,
    "focal_loss": {
      "alpha": 0.65,
      "gamma": 3
    }
  },
  "fast_adaptation_eval": {
    "device": "cpu",
    "checkpoint_mode": "last",
    "support_days": 1,
    "inner_lr": 0.01,
    "num_inner_updates": 5,
    "num_models": 8,
    "KL_weight": 1e-06,
    "classification_threshold": 0.35,
    "first_order": true,
    "svgd_bandwidth_scale": 1,
    "svgd_repulsive_weight": 1,
    "gamma_prior_concentration": 1,
    "gamma_prior_rate": 0.01,
    "normal_prior_loc": 0,
    "normal_prior_scale": 1,
    "focal_loss": {
      "alpha": 0.65,
      "gamma": 3
    }
  },
  "cold_start_eval": {
    "device": "cpu",
    "checkpoint_mode": "last",
    "support_days": 1,
    "num_models": 8,
    "classification_threshold": 0.35,
    "gamma_prior_concentration": 1,
    "gamma_prior_rate": 0.01,
    "normal_prior_loc": 0,
    "normal_prior_scale": 1,
    "focal_loss": {
      "alpha": 0.65,
      "gamma": 3
    }
  }
}
```

---

## 2. Dataset & Preprocessing Hyperparameters (Stage 1)

These parameters control how longitudinal tabular sensor streams (10-minute intervals, 144 steps/day) are converted into episodic tasks.

| Parameter | Type | Default | Recommended Range | Description & Mathematical Role |
| :--- | :---: | :---: | :---: | :--- |
| **`support_days`** | `int` | `1` | `1` – `3` | Number of continuous days in the support set $\mathcal{D}_s$. With 10-minute intervals, $K = \text{support\_days} \times 144$ time steps (e.g., $1 \text{ day} = 144$ steps). |
| **`query_days`** | `int` | `2` | `1` – `4` | Number of continuous days in the query set $\mathcal{D}_q$. Evaluates how well the adapted model predicts subsequent smoking events ($2 \text{ days} = 288$ steps). |
| **`step_stride`** | `int` | `144` | `72` – `144` | Sliding window displacement in time steps when creating consecutive episodes from a subject's longitudinal timeline. `144` shifts the window by exactly 1 calendar day. |
| **`train_frac`** | `float` | `0.85` | `0.70` – `0.85` | Proportion of generated episodic tasks allocated to the meta-training set (`train_tasks.pt`). |
| **`val_frac`** | `float` | `0.15` | `0.10` – `0.20` | Proportion of generated episodic tasks allocated to the meta-validation set (`val_tasks.pt`). Used for periodic validation loss tracking and peak-accuracy checkpoint discovery. |
| **`test_frac`** | `float` | `0.0` | `0.0` – `0.15` | Proportion allocated to the meta-test task split. `0.0` is standard when evaluating directly on held-out subject test cases (`Tast_Case1_routine_tasks.pt`, `Tast_Case2_routine_tasks.pt`). |
| **`tsls_cap`** | `float` | `1220.0` | `720.0` – `1440.0` | Upper threshold in minutes for Time Since Last Smoke (`TSLS`). Extreme outlier gaps (e.g., non-wear days or extended abstinence) are clipped to `1220.0` before StandardScaler normalization. |
| **`seed`** | `int` | `42` | `Any int` | Global random seed for deterministic train/val task partitioning and reproducible episodic sampling. |

### Global Categorical Vocabularies
* **`LOCATION_CLASSES` (11 Categories)**: `'Home'`, `'Public Place'`, `'Office'`, `'Construction Site'`, `'Factory'`, `'Restaurant'`, `'Gym'`, `'Entertainment'`, `'Market'`, `'School'`, `'Hospital'`.
* **`EMP_STAT_CLASSES` (12 Categories)**: `10`, `11`, `12`, `13`, `14`, `101`, `102`, `103`, `104`, `105`, `106`, `108`.

---

## 3. Base Network Architectures (`FcNet` & `LogisticRegression`)

The pipeline supports two modular neural backbones defined in [`CommonModels.py`](file:///c:/Users/kunke/OneDrive/Desktop/BayesianMetalearning/few_shot_meta_learning/CommonModels.py):

### A. Fully Connected Network (`FcNet`)
A deep feedforward network designed for multi-feature tabular representations:

| Parameter | Type | Default | Options | Description & Impact |
| :--- | :---: | :---: | :---: | :--- |
| **`num_hidden_units`** | `list[int]` | `[64, 64]` | `[40, 40]`, `[64, 64]`, `[128, 64]`, `[64, 64, 32]` | Hidden layer dimensions. `[64, 64]` yields ~6,146 trainable parameters, providing ample capacity for non-linear feature interactions without overfitting 1-day support sets. |
| **`activation`** | `str` | `"leaky_relu"` | `"leaky_relu"`, `"relu"`, `"gelu"`, `"elu"`, `"tanh"` | Activation function between linear layers. `"leaky_relu"` prevents dead neurons on sparse zero-padded tabular features; `"gelu"` provides smooth probabilistic gating. |
| **`dropout_rate`** | `float` | `0.0` | `0.0` – `0.3` | Dropout probability applied after each hidden layer. `0.0` is standard for few-shot meta-learning to avoid injecting excessive noise into inner-loop gradients. |
| **`use_layernorm`** | `bool` | `false` | `true`, `false` | When `true`, inserts `nn.LayerNorm(dim)` after each linear layer before activation. Normalizes activation variance across heterogeneous subject tasks. LayerNorm scales are initialised to 1 by every hyper-net (see §11); before that fix they started at 0 and the network could not learn at all. |
| **`num_ways`** | `int` | `2` | `2` | Output dimension / number of classes (Class 0: Not Smoking, Class 1: Smoking). |

### B. Logistic Regression (`LogisticRegression`)
A single linear projection module:
$$\hat{y} = \mathbf{w}^T \mathbf{x} + b$$
* **Trainable Parameters**: Exactly $27 \times 2 + 2 = 56$ parameters.
* **Characteristics**: Fast, convex, and computationally lightweight. Serves as the canonical linear baseline to test whether meta-learned representations outperform non-linear deep feature learning.

---

## 4. Meta-Training Hyperparameters (Stage 2)

These parameters govern the outer-loop meta-optimization across sampled training episodes:

| Parameter | Type | Default | Recommended Range | Description & Mathematical Role |
| :--- | :---: | :---: | :---: | :--- |
| **`device`** | `str` | `"cpu"` | `"cpu"`, `"cuda"` | Execution device for tensor operations and backpropagation. Automatically falls back to `"cpu"` if `"cuda"` is requested but unavailable. |
| **`num_epochs`** | `int` | `100` | `20` – `100` | Total outer meta-training epochs. Checkpoints (`Epoch_{e}.pt`) and `training_history.json` are written after each epoch. |
| **`num_episodes_per_epoch`** | `int` | `1000` | `250` – `1000` | Number of episodic tasks sampled from `train_tasks.pt` per epoch. |
| **`minibatch`** | `int` | `5` | `2` – `10` | Outer-loop meta-batch size $B$. Meta-gradients are accumulated across $B$ independent task episodes before taking an Adam step: $\theta \leftarrow \theta - \gamma \frac{1}{B} \sum_{i=1}^B \nabla_\theta \mathcal{L}_{\text{query}}^{(i)}$. |
| **`minibatch_print`** | `int` | `250` | `50` – `500` | Episode logging frequency for console training loss and accuracy updates. |
| **`validate_every_n_epochs`** | `int` | `1` | `0` – `10` | Run validation (`num_episodes` = 100 tasks from `val_tasks.pt`) at the end of every N-th epoch; the final epoch is always validated when N > 0. `0` disables validation entirely: about 8–9 % faster training (measured), `val_acc`/`val_nll` are recorded as `null`, no `best_model.pt` is written, and evaluation must use `checkpoint_mode: "last"`. `Epoch_{e}.pt` is still saved every epoch. |
| **`meta_lr`** | `float` | `0.001` | `1e-4` – `3e-3` | Outer-loop learning rate $\gamma$ for the Adam optimizer updating global meta-parameters. |
| **`inner_lr`** | `float` | `0.01` | `0.005` – `0.05` | Task adaptation step size $\alpha$. Used for task gradient descent: $\theta' = \theta - \alpha \nabla_\theta \mathcal{L}_{\text{train}}(x_s, y_s)$. |
| **`num_inner_updates`** | `int` | `5` | `3` – `7` | Number of gradient adaptation steps $K$ performed on the support set during task adaptation. |
| **`first_order`** | `bool` | `true` | `true`, `false` | When `true`, uses First-Order MAML (FOMAML), dropping second-order Hessian terms ($\nabla_\theta \nabla_\theta$) for ~3x faster training and reduced memory footprint. |
| **`num_models`** | `int` | `8` | `4` – `16` | Number of Monte Carlo parameter samples $M$ (for VAMPIRE, PLATIPUS, ABML) or ensemble particles (for BMAML) drawn to approximate expectations: $\mathbb{E}_{q(\theta)}[\mathcal{L}(f_\theta(x), y)] \approx \frac{1}{M}\sum_{m=1}^M \mathcal{L}(f_{\theta^{(m)}}(x), y)$. |
| **`KL_weight`** | `float` | `1e-6` | `1e-8` – `1e-4` | Multiplier $\beta$ scaling the Kullback-Leibler divergence $D_{\text{KL}}(q \parallel p)$ relative to the task prediction loss in variational algorithms (VAMPIRE, PLATIPUS, ABML). |
| **`classification_threshold`** | `float` | `0.35` | `0.20` – `0.50` | Probability cutoff threshold $\tau$ for assigning positive smoking class 1: $\hat{y} = 1 \iff P(y=1 \mid x) \ge \tau$. Setting $\tau = 0.35$ improves recall on imbalanced smoking events. |
| **`svgd_bandwidth_scale`** | `float` | `1.0` | `0.5` – `2.0` | *(BMAML)* Multiplier scaling the median-heuristic RBF kernel bandwidth $h = \text{median\_dist} / \log(M)$. |
| **`svgd_repulsive_weight`** | `float` | `1.0` | `0.5` – `3.0` | *(BMAML)* Multiplier on the repulsive gradient $\nabla_{\theta_j} k(\theta_i, \theta_j)$ preventing particle collapse. |
| **`gamma_prior_concentration`** | `float` | `1.0` | `0.5` – `2.0` | *(ABML)* Gamma prior shape hyperparameter $\alpha_0$ on precision $\tau = \exp(-2 \log \sigma)$. |
| **`gamma_prior_rate`** | `float` | `0.01` | `0.001` – `0.1` | *(ABML)* Gamma prior rate hyperparameter $\beta_0$ on precision $\tau$. |
| **`normal_prior_loc`** | `float` | `0.0` | `-1.0` – `1.0` | *(ABML)* Prior mean $\mu_0$ on variational weight distribution means. |
| **`normal_prior_scale`** | `float` | `1.0` | `0.5` – `2.0` | *(ABML)* Prior standard deviation $\sigma_0$ on variational weight distribution means. |

---

## 5. Fast Adaptation Evaluation Hyperparameters (Stage 3)

These parameters control task-specific fine-tuning on unseen downstream test cases (e.g., `Tast_Case1_routine_tasks.pt`, `Tast_Case2_routine_tasks.pt`):

| Parameter | Type | Default | Description |
| :--- | :---: | :---: | :--- |
| **`device`** | `str` | `"cpu"` | Hardware device (`"cpu"` or `"cuda"`) for evaluation inference. |
| **`checkpoint_mode`** | `str` | `"last"` | Checkpoint selection strategy handled by `find_best_checkpoint(log_dir, mode)`: <br>• `"best"`: Automatically selects the epoch with the highest validation accuracy recorded in `training_history.json`. <br>• `"last"`: Loads the final trained epoch checkpoint (`Epoch_100.pt`). |
| **`support_days`** | `int` | `1` | Number of days provided in the test case support set for adaptation fine-tuning ($1 \text{ day} = 144$ time steps). |
| **`inner_lr`** | `float` | `0.01` | Task adaptation step size $\alpha$ applied during test-time support fine-tuning. |
| **`num_inner_updates`** | `int` | `5` | Number of gradient updates $K$ applied to the support set before evaluating on the query set. |
| **`first_order`** | `bool` | `true` | Gradient mode during test-time adaptation. |
| **`num_models`** | `int` | `8` | Number of Monte Carlo ensemble models evaluated on the query set. Predictions are ensembled: $\bar{P}(y=1 \mid x) = \frac{1}{M}\sum_{m=1}^M P(y=1 \mid x, \theta^{(m)})$. |
| **`KL_weight`** | `float` | `1e-6` | Regularization weight applied during Bayesian inner-loop adaptation. |
| **`classification_threshold`** | `float` | `0.35` | Decision threshold $\tau$ used to compute Confusion Matrices, Precision, Recall, Accuracy, and Macro F1-Score on test query sets. |
| **`svgd_bandwidth_scale`** | `float` | `1.0` | *(BMAML)* Multiplier on the RBF kernel bandwidth during test-time SVGD updates. |
| **`svgd_repulsive_weight`** | `float` | `1.0` | *(BMAML)* Repulsive force weight during test-time particle adaptation. |
| **`gamma_prior_concentration`** | `float` | `1.0` | *(ABML)* Gamma prior shape parameter during adaptation. |
| **`gamma_prior_rate`** | `float` | `0.01` | *(ABML)* Gamma prior rate parameter during adaptation. |
| **`normal_prior_loc`** | `float` | `0.0` | *(ABML)* Normal prior mean during adaptation. |
| **`normal_prior_scale`** | `float` | `1.0` | *(ABML)* Normal prior scale during adaptation. |

---

## 6. Cold-Start / Zero-Shot Evaluation Hyperparameters (Stage 4)

Evaluates the raw meta-learned prior distribution directly on the query set without performing any support set adaptation ($K = 0$):

| Parameter | Type | Default | Description |
| :--- | :---: | :---: | :--- |
| **`device`** | `str` | `"cpu"` | Hardware device for cold-start inference. |
| **`checkpoint_mode`** | `str` | `"last"` | Checkpoint selection strategy (`"best"` or `"last"`). |
| **`support_days`** | `int` | `1` | Support day offset used to isolate the query sequence (Days 2–3). |
| **`num_inner_updates`** | `int` | `0` | Strictly `0`. Bypasses support set adaptation to evaluate zero-shot generalization of the global prior. |
| **`num_models`** | `int` | `8` | Number of models sampled directly from the global prior $p(\theta)$ for ensemble prediction. |
| **`classification_threshold`** | `float` | `0.35` | Decision threshold $\tau$ for evaluating cold-start query performance. |
| **`gamma_prior_concentration`** | `float` | `1.0` | *(ABML)* Prior shape parameter. |
| **`gamma_prior_rate`** | `float` | `0.01` | *(ABML)* Prior rate parameter. |
| **`normal_prior_loc`** | `float` | `0.0` | *(ABML)* Prior mean parameter. |
| **`normal_prior_scale`** | `float` | `1.0` | *(ABML)* Prior scale parameter. |

---

## 7. Focal Loss Hyperparameters

To combat severe class imbalance (where routine non-smoking class 0 accounts for >90% of samples), Focal Loss replaces standard Cross-Entropy:

$$\text{FL}(p_t) = -\alpha_t (1 - p_t)^\gamma \log(p_t)$$

where:
$$p_t = \begin{cases} p & \text{if } y = 1 \\ 1 - p & \text{if } y = 0 \end{cases}, \quad \alpha_t = \begin{cases} \alpha & \text{if } y = 1 \\ 1 - \alpha & \text{if } y = 0 \end{cases}$$

| Parameter | Key in Config | Type | Default | Recommended Range | Description & Mathematical Role |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Class Weighting** | **`alpha`** | `float` | `0.65` | `0.50` – `0.80` | Weight $\alpha$ assigned to the positive class (Smoking, 1), with $(1 - \alpha) = 0.35$ assigned to the negative class (Not Smoking, 0). Placing $\alpha = 0.65$ strongly penalizes false negatives on rare smoking events. |
| **Focusing Parameter** | **`gamma`** | `float` | `3.0` | `1.0` – `4.0` | Modulating exponent $\gamma \ge 0$. Dynamically dampens the loss contribution from well-classified easy examples ($(1 - p_t)^\gamma \to 0$), compelling the gradient updates to focus on ambiguous and hard-to-classify smoking transitions. |

---

## 8. Algorithm-Specific Deep Dive

### A. MAML (Model-Agnostic Meta-Learning)
* **File**: [`Maml.py`](file:///c:/Users/kunke/OneDrive/Desktop/BayesianMetalearning/few_shot_meta_learning/Maml.py)
* **Weight Representation**: Deterministic point estimate $\theta$.
* **Key Mechanisms**:
  * Inner Adaptation: $\theta' = \theta - \alpha \nabla_\theta \mathcal{L}(x_s, y_s)$
  * Outer Objective: $\min_\theta \sum_i \mathcal{L}_{\text{query}}^{(i)}(\theta'_i)$
  * Supports First-Order MAML (`first_order: true`) and Full Second-Order Hessian backpropagation.

### B. PLATIPUS (Probabilistic MAML with Variational Sampling)
* **File**: [`Platipus.py`](file:///c:/Users/kunke/OneDrive/Desktop/BayesianMetalearning/few_shot_meta_learning/Platipus.py)
* **Weight Representation**: Amortized variational initializations $\theta^{(0)} \sim q(\theta \mid \mathcal{D}_s)$.
* **Key Mechanisms**:
  * Samples task-adapted weights using meta-learned step sizes $\gamma_p$ and $\gamma_q$.
  * Scales outer-loop KL divergence between the adapted distribution and the global prior using `KL_weight` ($\beta = 1\times 10^{-6}$).

### C. VAMPIRE (Variational Meta-Learner with Pointwise Regularization)
* **File**: [`Vampire2.py`](file:///c:/Users/kunke/OneDrive/Desktop/BayesianMetalearning/few_shot_meta_learning/Vampire2.py)
* **Weight Representation**: Diagonal Gaussian distribution $\mathcal{N}(\mu, \operatorname{diag}(\sigma^2))$.
* **Key Mechanisms**:
  * Re-parameterization trick: $\theta^{(m)} = \mu + \epsilon \odot \sigma$, where $\epsilon \sim \mathcal{N}(0, I)$.
  * Evaluates $M = 8$ Monte Carlo parameter samples per task.
  * Penalizes deviation from a standard Gaussian prior via $D_{\text{KL}}(q \parallel \mathcal{N}(0, I))$.

### D. ABML (Amortized Bayesian Meta-Learning)
* **File**: [`Abml.py`](file:///c:/Users/kunke/OneDrive/Desktop/BayesianMetalearning/few_shot_meta_learning/Abml.py)
* **Weight Representation**: Variational Gaussian $\mathcal{N}(\mu, \operatorname{diag}(\sigma^2))$ with conjugate priors on precision and mean.
* **Key Mechanisms**:
  * Precision $\tau = \exp(-2 \log \sigma)$ is governed by a Gamma prior: $\tau \sim \text{Gamma}(\alpha_0, \beta_0)$ where $\alpha_0 = \text{gamma\_prior\_concentration} = 1.0$ and $\beta_0 = \text{gamma\_prior\_rate} = 0.01$.
  * Means $\mu$ are governed by a Normal prior: $\mu \sim \mathcal{N}(\mu_0, \sigma_0^2)$ where $\mu_0 = \text{normal\_prior\_loc} = 0.0$ and $\sigma_0 = \text{normal\_prior\_scale} = 1.0$.
  * Optimizes the task-specific Evidence Lower Bound (ELBO) with `KL_weight` ($\beta = 1\times 10^{-6}$).

### E. BMAML (Bayesian MAML with Stein Variational Gradient Descent)
* **File**: [`Bmaml.py`](file:///c:/Users/kunke/OneDrive/Desktop/BayesianMetalearning/few_shot_meta_learning/Bmaml.py)
* **Weight Representation**: Non-parametric ensemble of $M = 8$ particles $\{\theta^{(m)}\}_{m=1}^M$.
* **Key Mechanisms**:
  * Inner-loop updates drive particles via Stein Variational Gradient Descent:
    $$\theta_i \leftarrow \theta_i - \alpha \left[ \sum_{j=1}^M k(\theta_j, \theta_i) \nabla_{\theta_j} \mathcal{L}(x_s, y_s) - \lambda_{\text{rep}} \sum_{j=1}^M \nabla_{\theta_j} k(\theta_j, \theta_i) \right]$$
  * **RBF Kernel Bandwidth**: $h = \left( \frac{\text{median\_dist}}{\log(M)} \right) \times \text{svgd\_bandwidth\_scale}$, where `median_dist` is the median of the full $M \times M$ pairwise-distance matrix (zero diagonal included), identical to upstream `master` and the original BMAML `'org'` kernel. $h$ is floored at $10^{-5}$ only to avoid NaN if all particles collapse.
  * **Repulsive Force**: $\lambda_{\text{rep}} = \text{svgd\_repulsive\_weight} = 1.0$ prevents particle collapse.
  * *(Note: BMAML does not use `KL_weight` because SVGD updates particles directly via kernelized Stein discrepancy rather than an analytical KL penalty).*

---

## 9. Algorithm Hyperparameter Applicability Matrix

| Hyperparameter | Key in `config.json` | MAML | VAMPIRE | PLATIPUS | ABML | BMAML |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Inner Learning Rate** | `inner_lr` | ✅ Active | ✅ Active | ✅ Active | ✅ Active | ✅ Active |
| **Inner Step Count** | `num_inner_updates` | ✅ Active | ✅ Active | ✅ Active | ✅ Active | ✅ Active |
| **First Order Mode** | `first_order` | ✅ Active | ✅ Active | ✅ Active | ✅ Active | ✅ Active |
| **Monte Carlo Ensemble Count** | `num_models` | ❌ Fixed (1) | ✅ Active (8) | ✅ Active (8) | ✅ Active (8) | ✅ Active (8 Particles) |
| **KL Divergence Regularization** | `KL_weight` | ❌ N/A | ✅ Active | ✅ Active | ✅ Active | ❌ N/A (Uses SVGD) |
| **Classification Threshold** | `classification_threshold` | ✅ Active | ✅ Active | ✅ Active | ✅ Active | ✅ Active |
| **SVGD Bandwidth Scale** | `svgd_bandwidth_scale` | ❌ N/A | ❌ N/A | ❌ N/A | ❌ N/A | ✅ Active |
| **SVGD Repulsive Weight** | `svgd_repulsive_weight` | ❌ N/A | ❌ N/A | ❌ N/A | ❌ N/A | ✅ Active |
| **Gamma Prior Concentration** | `gamma_prior_concentration` | ❌ N/A | ❌ N/A | ❌ N/A | ✅ Active | ❌ N/A |
| **Gamma Prior Rate** | `gamma_prior_rate` | ❌ N/A | ❌ N/A | ❌ N/A | ✅ Active | ❌ N/A |
| **Normal Prior Loc** | `normal_prior_loc` | ❌ N/A | ❌ N/A | ❌ N/A | ✅ Active | ❌ N/A |
| **Normal Prior Scale** | `normal_prior_scale` | ❌ N/A | ❌ N/A | ❌ N/A | ✅ Active | ❌ N/A |
| **Focal Loss** ($\alpha, \gamma$) | `focal_loss` | ✅ Active | ✅ Active | ✅ Active | ✅ Active | ✅ Active |
| **Checkpoint Mode** | `checkpoint_mode` | ✅ Active | ✅ Active | ✅ Active | ✅ Active | ✅ Active |

---

## 10. Hyperparameter Tuning Guide & Best Practices

### A. Tuning for Severe Class Imbalance (Smoking Event Detection)
1. **Decision Threshold ($\tau$)**:
   - Standard 0.50 cutoff causes severe false negatives because smoking events account for <10% of timestamps.
   - Setting `classification_threshold: 0.35` significantly boosts minority class recall while maintaining balanced precision.
2. **Focal Loss Weighting ($\alpha = 0.65, \gamma = 3$)**:
   - `gamma: 3` powerfully dampens gradients from routine easy non-smoking timestamps ($(1 - p_t)^3 \to 0$).
   - `alpha: 0.65` directs 65% of the gradient scale toward smoking event boundaries.

### B. Architecture Tuning
1. **Hidden Units (`num_hidden_units: [64, 64]`)**:
   - `[64, 64]` captures non-linear tabular interactions without parameter bloat (~6,146 parameters).
   - For ultra-fast baselines, switch to `LogisticRegression` (56 parameters).
2. **Activation (`activation: "leaky_relu"`)**:
   - Prevents dead neurons on sparse tabular inputs (such as one-hot locations and clipped zero values).

### C. Meta-Learning Stability
1. **Epochs & Checkpoint Selection (`checkpoint_mode`)**:
   - Training for `100` epochs allows deep convergence of Bayesian meta-priors.
   - Use `"best"` to automatically pick the peak validation accuracy epoch, or `"last"` to evaluate asymptotic meta-convergence.
2. **Inner Step Count (`num_inner_updates: 5`)**:
   - Keep between `3` and `7`. Values $> 10$ lead to inner-loop overfitting on small 1-day support sets.
3. **Ensemble Count (`num_models: 8`)**:
   - `8` provides excellent variance reduction for Monte Carlo predictions and SVGD particle coverage while keeping CPU evaluation runtime under 10 seconds per test case.
4. **BMAML Particle Dynamics**:
   - If particles collapse to a single point, increase `svgd_repulsive_weight` to `2.0` or reduce `svgd_bandwidth_scale` to `0.5`.
5. **Training Speed vs. Monitoring (`validate_every_n_epochs`)**:
   - Validation costs about 8–9 % of each epoch (measured on the tabular runs). Writing TensorBoard scalars, `training_history.json` and checkpoints costs milliseconds and is not worth removing.
   - Use `validate_every_n_epochs: 5` (or `0`) for faster sweeps; keep `1` when you need a per-epoch validation curve or `checkpoint_mode: "best"`.

---

## 11. Behaviour Changes vs. Upstream `master`

This branch keeps the meta-learning algorithms of upstream [`cnguyen10/few_shot_meta_learning`](https://github.com/cnguyen10/few_shot_meta_learning) (`master`). The changes below alter behaviour relative to `master`; results produced before and after them are not bit-comparable. For the complete list, including the replaced code, see [`CHANGES_FROM_MASTER.md`](CHANGES_FROM_MASTER.md).

| Area | File(s) | `master` | This branch | Effect on training |
| :--- | :--- | :--- | :--- | :--- |
| **KL divergence to N(0, I)** | `Vampire2.py`, `Simpa.py` (`KL_divergence_standard_normal`) | Subtracted `n` (number of parameter tensors) once per tensor | Subtracts `numel(p_mean)` per tensor, giving the exact $\tfrac12\sum_j(\mu_j^2+\sigma_j^2-1-2\log\sigma_j)$ | **VAMPIRE:** constant shift only, gradients unchanged; logged KL/loss values differ. **SIMPA:** KL sits inside `sqrt(...)` of the PAC-Bayes bound, so gradient scale changes. |
| **Validation cadence** | `MLBaseClass.py`, `Platipus.py` | Validation every `minibatch_print` episodes (4× per epoch with the tabular config) | End of every `validate_every_n_epochs`-th epoch (default 1) | None on meta-updates; fewer `Val_*` points in TensorBoard; ~25 % less wall-clock time at the default. |
| **Fresh-run clean-up** | `_utils.clear_run_artifacts` | Old files were overwritten one by one | When `resume_epoch == 0`, the run first deletes `Epoch_*.pt`, `best_model.pt` and `training_history.json` in its `logdir` | Prevents stale checkpoints/history from an older run being selected. TensorBoard event files are not deleted. |
| **Checkpoint metadata** | `_utils.save_epoch_checkpoint` | `{hyper_net_state_dict, opt_state_dict}` | Adds `epoch`, `val_acc`, `val_nll` (`null` when the epoch was not validated) | None; old and new checkpoints load the same way. |
| **`test()` checkpoint choice** | `_utils.resolve_test_epoch` | Loaded `resume_epoch`; with `resume_epoch = 0` it silently tested an **untrained** model | 1) final epoch of a `train()` call on the same object, 2) `resume_epoch` if > 0, 3) latest `Epoch_*.pt` in `logdir`; raises `FileNotFoundError` if none exists | None on training; train-then-test scripts now always score the model they just trained. |
| **BMAML bandwidth** | `Bmaml.py` (`get_kernel`) | Median of full distance matrix | Same as `master`; adds an optional `svgd_bandwidth_scale` (default 1.0) and a $10^{-5}$ floor on $h$ (only active if particles collapse, where `master` produced NaN) | None at default settings. |
| **PLATIPUS `mu_theta` init** | `HyperNetClasses.py` (`PlatipusNet`) | `torch.randn` (N(0, 1)) | Kaiming-normal weights / zero biases, same as the other four hyper-nets. **Kept intentionally** | Changes the starting point. With N(0, 1) the 64×64 FcNet starts with 100 % saturated logits on the tabular data. In 6-epoch PLATIPUS + FcNet runs (2 seeds) the master init ended with a 45–85× higher validation loss (0.76–1.44 vs 0.016–0.017) and a lower smoking-class F1 (0.22–0.26 vs 0.31–0.32). PLATIPUS + LogisticRegression (6 epochs, 2 seeds): master init ended with a 2.3–2.7× higher validation loss (0.042–0.061 vs 0.018–0.023), lower macro-F1 (0.35–0.40 vs 0.43–0.48), lower smoking-class F1 (0.16–0.19 vs 0.25–0.26) and lower precision (0.09–0.11 vs 0.15). |
| **LayerNorm scale init** | `_utils.py` (`intialize_parameters`, `layernorm_weight_keys`), `HyperNetClasses.py` | Not applicable (`master` has no LayerNorm). The shared init set every 1-D tensor to 0 | LayerNorm scales (γ) start at 1, all other 1-D tensors at 0 as before | Only when `use_layernorm: true`. With γ = 0, every row gets the same prediction and both hidden layers receive exactly zero gradient, so MAML + FcNet stayed at a flat val loss of 0.0222 (predicting "smoking" for every row) for 6 epochs. With γ = 1 it trains like LayerNorm off. Networks without LayerNorm, including BatchNorm in CNN/ResNet, are initialised exactly as in `master`. |
| **Binary accuracy** | `Maml.py`, `Abml.py`, `Bmaml.py`, `Vampire2.py`, `Platipus.py` (`evaluation`) | `argmax` | For 2 classes: $P(y=1) \ge$ `classification_threshold` (default 0.5); otherwise `argmax` | None (metric only). Validation accuracy and `"best"` checkpoint selection now depend on the threshold (0.35 in the tabular config) |
| **Configurable `FcNet`** | `CommonModels.py`, `Maml.py`, `Simpa.py`, `ProtoNet.py` | Fixed 40×40 ReLU | `num_hidden_units`, `activation`, `dropout_rate`, `use_layernorm` from config; defaults reproduce master | None at defaults; `main.py` never sets these keys |
| **`LogisticRegression` backbone** | `CommonModels.py`, `Maml.py`, `Simpa.py`, `ProtoNet.py` | Not available | Single `LazyLinear` layer | Additive |
| **ProtoNet architectures** | `ProtoNet.py` | CNN, ResNet18 | Adds `FcNet`, `LogisticRegression`; uses `config['train_val_split_function']` for lazy init | Additive (the missing `FcNet` import was fixed in the review) |
| **ABML priors / BMAML SVGD knobs** | `Abml.py`, `Bmaml.py` | Hard-coded | `gamma_prior_*`, `normal_prior_*`, `svgd_repulsive_weight`, `svgd_bandwidth_scale` from config | None at defaults |
| **PLATIPUS training loop** | `Platipus.py` | Validation inside print block; `test()` commented out | Same loop changes as `MLBaseClass`; `global_step` set at every epoch start (fixes `UnboundLocalError` when `num_episodes_per_epoch < minibatch_print`); working `test()` | None on meta-updates |
| **Device placement** | `Maml.py`, `ProtoNet.py` | Lazy init on CPU, then moved | Moved to `config['device']` before the dummy forward pass | None (fixes GPU device mismatch) |
| **Library compatibility** | `HyperNetClasses.py`, `CommonModels.py`, `EpisodeSampler.py` | `ParameterList(parameters=None)`, `append(parameter=...)`, torchvision `pretrained=`, `random.sample(dict.keys())` | Current PyTorch / torchvision / Python 3.11 APIs | None (master PLATIPUS fails to start on current PyTorch) |
| **`EpisodeSampler`** | `EpisodeSampler.py` | Wrong index offsets for ≥ 3 concatenated datasets; broken `__len__` | Cumulative offsets; single datasets supported; valid `__len__`; clear start-up errors if a class has too few samples or no dataset has `num_ways` classes (sampling stays without replacement) | Image benchmarks only (fixes sampling for ≥ 3 datasets) |
| **Regression split** | `_utils.train_val_split_regression` | `eps_data[i].T`, float `k_shot` | `.view(-1, 1)`, `int(k_shot)` | None for SineLine |
| **Pretrained checkpoints removed** | `meta_learning/maml/FcNet/Epoch_300.pt`, `meta_learning/vampire2/FcNet/Epoch_100.pt` | Shipped | Deleted; `*.pt` and `*.csv` git-ignored | `visualize_regression.ipynb` needs them; restore with `git checkout master -- meta_learning/` |
