# Changes from Upstream `master`: Complete Change Log

This document lists **every difference** between the upstream repository and this fork, with the code that was replaced and the code that replaced it.

| | |
|---|---|
| **Baseline** | `master` @ `d797d9d` ("Implement Simpa method"), identical to upstream [`cnguyen10/few_shot_meta_learning`](https://github.com/cnguyen10/few_shot_meta_learning) `master` |
| **Compared state** | Branch `MyEdits2` @ `8294b2f` **plus** the uncommitted working-tree changes from the 2026-10-04 → 2026-10-06 review |
| **Fork** | https://github.com/ShubhamKunkerkar/few_shot_meta_learning |
| **Size** | 102 files changed. Original Python modules: 12 files, +518 / −152 lines (whitespace ignored). New tabular subsystem: 42 files. Committed run artifacts: 38 files. Documentation: 3 new Markdown files plus a link in `README.md`. Repository/environment files: 4. Pretrained checkpoints: 2 removed. |

**Origin tags** used throughout:
- **[Branch]**: committed on `MyEdits2` (commits `f7c939b` … `8294b2f`).
- **[Review]**: made during the review, **not yet committed**.
- **[Branch → Review]**: introduced on the branch, then changed during the review. The intermediate branch code is shown too.

**Effect tags:**
- **TRAINING**: changes what a model learns.
- **BEHAVIOUR**: changes program behaviour, but not the meta-learning maths.
- **COMPAT**: keeps the code running on newer library versions, with no semantic change.
- **ADDITIVE**: new option or feature; the default reproduces `master`.
- **COSMETIC**: formatting or comments only.

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Changes That Affect Training Results](#2-changes-that-affect-training-results)
3. [Cross-Cutting Change: Binary Classification Accuracy](#3-cross-cutting-change-binary-classification-accuracy)
4. [File-by-File Changes to Original Modules](#4-file-by-file-changes-to-original-modules)
   - [4.1 `_utils.py`](#41-_utilspy)
   - [4.2 `MLBaseClass.py`](#42-mlbaseclasspy)
   - [4.3 `Platipus.py`](#43-platipuspy)
   - [4.4 `Maml.py`](#44-mamlpy)
   - [4.5 `Abml.py`](#45-abmlpy)
   - [4.6 `Bmaml.py`](#46-bmamlpy)
   - [4.7 `Vampire2.py`](#47-vampire2py)
   - [4.8 `Simpa.py`](#48-simpapy)
   - [4.9 `ProtoNet.py`](#49-protonetpy)
   - [4.10 `HyperNetClasses.py`](#410-hypernetclassespy)
   - [4.11 `CommonModels.py`](#411-commonmodelspy)
   - [4.12 `EpisodeSampler.py`](#412-episodesamplerpy)
5. [Removed Files](#5-removed-files)
6. [Repository Configuration Files](#6-repository-configuration-files)
7. [New Documentation Files](#7-new-documentation-files)
8. [New Tabular Subsystem (`tabular/`)](#8-new-tabular-subsystem-tabular)
9. [Unchanged Files](#9-unchanged-files)
10. [Verification Performed](#10-verification-performed)
11. [Known Open Issues (Not Changed)](#11-known-open-issues-not-changed)

---

## 1. Executive Summary

| File | What changed | Origin | Effect |
|---|---|---|---|
| `_utils.py` | `train_val_split_regression` reshape; `intialize_parameters` supports LayerNorm scales; new checkpoint/history helpers; new `FocalLoss` | Branch, Review | TRAINING (LayerNorm only), BEHAVIOUR, ADDITIVE |
| `MLBaseClass.py` | Validation moved to end of epoch with `validate_every_n_epochs`; checkpoint metadata, `training_history.json`, `best_model.pt`; fresh-run clean-up; `test()` checkpoint resolution | Branch → Review | BEHAVIOUR |
| `Platipus.py` | Same training-loop changes as `MLBaseClass`; `global_step` crash fixed; `test()` implemented; threshold accuracy | Branch → Review | BEHAVIOUR |
| `Maml.py` | Configurable `FcNet`; `LogisticRegression` backbone; device placement before lazy init; threshold accuracy | Branch | ADDITIVE, COMPAT |
| `Abml.py` | Prior hyper-parameters read from config; threshold accuracy | Branch | ADDITIVE |
| `Bmaml.py` | `svgd_repulsive_weight`, `svgd_bandwidth_scale`, NaN floor on `h`; threshold accuracy. Kernel median **restored to master** | Branch → Review | ADDITIVE (default = master) |
| `Vampire2.py` | Exact KL constant; threshold accuracy | Branch, Review (docs) | Loss value only (gradients unchanged) |
| `Simpa.py` | Exact KL constant; architecture dict; `LogisticRegression` | Branch, Review (docs) | **TRAINING** (KL inside `sqrt`) |
| `ProtoNet.py` | Architecture dict with `FcNet` / `LogisticRegression`; device placement; missing `FcNet` import fixed | Branch → Review | ADDITIVE |
| `HyperNetClasses.py` | `ParameterList` API update; PLATIPUS `mu_theta` init; LayerNorm scale init | Branch, Review | **TRAINING** (PLATIPUS), COMPAT |
| `CommonModels.py` | `FcNet` rewritten (activation, dropout, LayerNorm, depth 0); torchvision API compatibility; new `LogisticRegression` | Branch, Review | ADDITIVE (defaults = master), COMPAT |
| `EpisodeSampler.py` | Cumulative index offsets fixed; single datasets supported; `__len__` fixed; start-up validation | Branch → Review | BEHAVIOUR (bug fixes) |
| `meta_learning/*.pt` | Authors' pretrained checkpoints removed | Branch | Breaks `visualize_regression.ipynb` |
| `.gitignore`, `.gitattributes`, `environment.yml`, `condaenv.*.txt` | Ignore rules, environment files | Branch | — |
| `AGENTS.md`, `HYPERPARAMETERS.md`, `CHANGES_FROM_MASTER.md` | New documentation | Branch, Review | — |
| `tabular/**` | Entire tabular meta-learning subsystem (42 files) plus committed run artifacts (38 files) | Branch, Review | ADDITIVE |

`main.py`, `RegressionDataset.py`, `visualize_regression.ipynb`, `README.md` (apart from a link to this document) and `LICENSE` are unchanged.

---

## 2. Changes That Affect Training Results

Only these changes alter what a model learns relative to `master`. Everything else is behaviour, compatibility, additive options or documentation.

| # | Change | Algorithms affected | When it applies | Measured impact |
|---|---|---|---|---|
| 1 | PLATIPUS `mu_theta` init: `torch.randn` → Kaiming-normal / zeros (§4.10) | PLATIPUS | Always | Tabular, 6 epochs, 2 seeds per backbone. **FcNet:** master init loss 45–85× higher (0.76–1.44 vs 0.016–0.017), smoking-class F1 0.22–0.26 vs 0.31–0.32. **LogReg:** master init loss 2.3–2.7× higher, macro-F1 0.35–0.40 vs 0.43–0.48 |
| 2 | Exact KL constant `- n` → `- numel(p_mean)` (§4.7, §4.8) | **SIMPA** (training), VAMPIRE (loss value only) | Always | SIMPA: KL is inside `sqrt(...)`, so the gradient scale changes. VAMPIRE: gradients identical |
| 3 | LayerNorm scale (γ) initialised to 1 instead of 0 (§4.1, §4.10) | All algorithms using `FcNet` | Only with `use_layernorm=True` | With γ = 0 the network could not learn at all (flat loss 0.0222 for 6 epochs) |
| 4 | `EpisodeSampler` index offsets (§4.12) | Image benchmarks via `main.py` | Only with ≥ 3 concatenated datasets | Master sampled wrong images for the 3rd and later datasets |

**Not training-affecting at default settings:** BMAML kernel (median restored to master; new knobs default to 1.0), validation cadence (no backward pass), accuracy thresholding (metric only), all checkpoint/history changes.

**Verified bit-identical (same seed):** PLATIPUS trained on the committed branch code and on the working tree gives identical validation losses to 10 decimals for both backbones and both initialisations (§10).

---

## 3. Cross-Cutting Change: Binary Classification Accuracy

**Origin:** [Branch] · **Effect:** metric only (loss, gradients and training unchanged) · **Files:** `Maml.py`, `Abml.py`, `Bmaml.py`, `Vampire2.py`, `Platipus.py` (`evaluation()` in each)

For binary problems (2 classes), accuracy now uses a configurable probability threshold on class 1 instead of `argmax`. With the default `classification_threshold = 0.5` this equals `argmax`, except on exact ties at p = 0.5. Multi-class and regression (`num_ways = 1`) take the original `argmax` path.

**Before (`master`), in `Abml.py`, `Bmaml.py`, `Vampire2.py`, `Platipus.py`:**
```python
        y_pred = y_pred / len(logits)

        accuracy = (y_pred.argmax(dim=1) == y_v).float().mean().item()

        return loss.item(), accuracy * 100
```

**After:**
```python
        y_pred = y_pred / len(logits)

        cls_threshold = float(self.config.get('classification_threshold', 0.5))
        if y_pred.shape[1] == 2:
            accuracy = ((y_pred[:, 1] >= cls_threshold).long() == y_v).float().mean().item()
        else:
            accuracy = (y_pred.argmax(dim=1) == y_v).float().mean().item()

        return loss.item(), accuracy * 100
```

**Before (`master`), in `Maml.py`** (MAML has a single logit tensor):
```python
        loss = self.config['loss_function'](input=logits, target=y_v)

        accuracy = (logits.argmax(dim=1) == y_v).float().mean().item()

        return loss.item(), accuracy * 100
```

**After:**
```python
        loss = self.config['loss_function'](input=logits, target=y_v)

        cls_threshold = float(self.config.get('classification_threshold', 0.5))
        if logits.shape[1] == 2:
            probs = torch.softmax(input=logits, dim=1)
            accuracy = ((probs[:, 1] >= cls_threshold).long() == y_v).float().mean().item()
        else:
            accuracy = (logits.argmax(dim=1) == y_v).float().mean().item()

        return loss.item(), accuracy * 100
```

`ProtoNet.evaluation()` and `Simpa` still use `argmax`.

---

## 4. File-by-File Changes to Original Modules

### 4.1 `_utils.py`

#### 4.1.1 New imports
**Origin:** [Review] · COSMETIC
```python
# added after "import csv"
import glob
import json
import re
```

#### 4.1.2 `train_val_split_regression`: reshape and integer `k_shot`
**Origin:** [Branch] · COMPAT (identical result for SineLine)

**Before:**
```python
def train_val_split_regression(eps_data: typing.List[torch.Tensor], k_shot: float) -> typing.Dict[str, torch.Tensor]:
    """split the data for regression
    Args:
        eps_data: a list of 2 tensors: x and y
        k_shot:
    """
    data = {}

    v_ids = [i for i in range(eps_data[0].numel())]

    k_ids = random.sample(population=v_ids, k=k_shot)
    v_ids = [v for v in v_ids if v not in k_ids]

    # due to the usage of data loader, the data is in shape (1, num_samples)
    # hence, we need to transpose to get the format of mini-batch of samples
    eps_data_batch = [eps_data[i].T for i in range(len(eps_data))]
```

**After:**
```python
def train_val_split_regression(eps_data: typing.List[torch.Tensor], k_shot: int) -> typing.Dict[str, torch.Tensor]:
    """split the data for regression
    Args:
        eps_data: a list of 2 tensors: x and y
        k_shot:
    """
    data = {}
    k_shot = int(k_shot)

    v_ids = [i for i in range(eps_data[0].numel())]

    k_ids = random.sample(population=v_ids, k=k_shot)
    v_ids = [v for v in v_ids if v not in k_ids]

    # Reshape each component to (num_samples, 1) so 1D inputs have feature dim 1
    eps_data_batch = [eps_data[i].view(-1, 1) for i in range(len(eps_data))]
```
For the `(1, num_samples)` tensors produced by the SineLine data loader, `.T` and `.view(-1, 1)` give the same `(num_samples, 1)` result. `int(k_shot)` prevents `random.sample` from failing on a float.

#### 4.1.3 `intialize_parameters`: LayerNorm scales start at 1 (new `layernorm_weight_keys`)
**Origin:** [Review] · TRAINING (only when `FcNet(use_layernorm=True)`)

**Before:**
```python
def intialize_parameters(state_dict: dict) -> typing.List[torch.Tensor]:
    """"""
    p = list(state_dict.values())
    for m in p:
        if m.ndim > 1:
            torch.nn.init.kaiming_normal_(tensor=m, nonlinearity='relu')
        else:
            torch.nn.init.zeros_(tensor=m)
    
    return p
```

**After:**
```python
def layernorm_weight_keys(module: torch.nn.Module) -> typing.Set[str]:
    """state_dict keys of the scale (gamma) of every LayerNorm in module
    """
    return {'{0:s}.weight'.format(name) for name, m in module.named_modules() if isinstance(m, torch.nn.LayerNorm) and m.elementwise_affine}

def intialize_parameters(state_dict: dict, ones_keys: typing.Collection[str] = ()) -> typing.List[torch.Tensor]:
    """Kaiming-normal for weights (ndim > 1), zeros for 1-D tensors (biases)

    Args:
        state_dict: state dictionary of the base network
        ones_keys: keys of 1-D tensors initialized to ones instead of zeros, i.e. LayerNorm scales.
            A LayerNorm scale of 0 outputs a constant and blocks all gradients below it, so the
            network cannot learn (see layernorm_weight_keys). Empty by default, which keeps
            master's behaviour for every network without LayerNorm (including BatchNorm in CNN/ResNet).
    """
    p = list(state_dict.values())
    for key, m in state_dict.items():
        if m.ndim > 1:
            torch.nn.init.kaiming_normal_(tensor=m, nonlinearity='relu')
        elif key in ones_keys:
            torch.nn.init.ones_(tensor=m)
        else:
            torch.nn.init.zeros_(tensor=m)
    
    return p
```

**Why:** the original function zeroes every 1-D tensor, which was meant for biases. The branch added LayerNorm to `FcNet`, and LayerNorm's scale is also 1-D, so it started at 0. In that state every row gets the same prediction, and 8 of 10 parameter groups (all hidden-layer weights) receive exactly zero gradient. MAML + FcNet with LayerNorm stayed at a flat validation loss of 0.0222 for 6 epochs, predicting "smoking" for every row. With the fix it trains like LayerNorm off.

**Scope:** `ones_keys` only lists LayerNorm scales. FcNet without LayerNorm, LogisticRegression and master's CNN/ResNet (including affine BatchNorm) are verified to get exactly master's initialisation. All four hyper-nets pass the keys (§4.10).

#### 4.1.4 New checkpoint and training-history helpers
**Origin:** [Review] (logic introduced on the branch inside `MLBaseClass.train` / `Platipus.train`, moved here and corrected) · BEHAVIOUR

These replace two identical copies of checkpoint/history code (in `MLBaseClass.train` and `Platipus.train`), so the two loops can't drift apart. All of this code is new.

```python
# --------------------------------------------------
# Checkpoint / training-history helpers shared by MLBaseClass.train and Platipus.train
# --------------------------------------------------
def list_epoch_checkpoints(logdir: str) -> typing.Dict[int, str]:
    """Map epoch number -> path for every Epoch_{N}.pt file in logdir
    """
    checkpoints = {}
    for path in glob.glob(os.path.join(logdir, 'Epoch_*.pt')):
        match = re.fullmatch(r'Epoch_(\d+)\.pt', os.path.basename(path))
        if match:
            checkpoints[int(match.group(1))] = path
    return checkpoints

def clear_run_artifacts(logdir: str) -> None:
    """Remove Epoch_*.pt, best_model.pt and training_history.json left in logdir by a previous run.

    Called when a fresh run starts (resume_epoch == 0), so that files of an older (e.g. longer) run
    cannot be picked up as if they belonged to the new run.
    """
    stale_files = list(list_epoch_checkpoints(logdir=logdir).values())
    stale_files += [os.path.join(logdir, 'best_model.pt'), os.path.join(logdir, 'training_history.json')]

    num_removed = 0
    for path in stale_files:
        if os.path.isfile(path):
            os.remove(path)
            num_removed += 1

    if num_removed > 0:
        print('Fresh run (resume_epoch = 0): removed {0:d} file(s) left by a previous run in {1:s}\n'.format(num_removed, logdir))

def validation_due(config: dict, epoch_id: int) -> bool:
    """Whether validation runs at the end of epoch_id (0-based)

    config['validate_every_n_epochs'] (default 1 = every epoch, 0 = never). When enabled,
    the final epoch is always validated.
    """
    every_n_epochs = int(config.get('validate_every_n_epochs', 1))
    if every_n_epochs <= 0:
        return False

    final_epoch = config['resume_epoch'] + config['num_epochs']
    return ((epoch_id + 1) % every_n_epochs == 0) or (epoch_id + 1 == final_epoch)

def save_epoch_checkpoint(
    model: dict,
    config: dict,
    epoch_id: int,
    val_nll: typing.Optional[float],
    val_acc: typing.Optional[float]
) -> str:
    """Save Epoch_{N}.pt, update training_history.json and keep best_model.pt (highest val_acc)

    val_nll and val_acc are None for epochs that were not validated; such epochs are recorded
    in the history but are never selected as the best checkpoint.

    Returns: path of the saved checkpoint
    """
    checkpoint = {
        "epoch": epoch_id + 1,
        "hyper_net_state_dict": model["hyper_net"].state_dict(),
        "opt_state_dict": model["optimizer"].state_dict(),
        "val_acc": val_acc,
        "val_nll": val_nll
    }
    checkpoint_path = os.path.join(config['logdir'], 'Epoch_{0:d}.pt'.format(epoch_id + 1))
    torch.save(obj=checkpoint, f=checkpoint_path)

    # maintain training history & best checkpoint
    history_file = os.path.join(config['logdir'], 'training_history.json')
    history = {"epochs": [], "best_checkpoint": None}
    if os.path.exists(history_file):
        try:
            with open(history_file, 'r', encoding='utf-8') as hf:
                history = json.load(hf)
        except Exception:
            history = {"epochs": [], "best_checkpoint": None}

    epoch_record = {
        "epoch": epoch_id + 1,
        "val_acc": val_acc,
        "val_nll": val_nll,
        "checkpoint_path": checkpoint_path
    }
    history["epochs"].append(epoch_record)

    curr_best = history.get("best_checkpoint")
    if val_acc is not None and (curr_best is None or curr_best.get("val_acc") is None or val_acc >= curr_best["val_acc"]):
        history["best_checkpoint"] = epoch_record
        torch.save(obj=checkpoint, f=os.path.join(config['logdir'], 'best_model.pt'))

    try:
        with open(history_file, 'w', encoding='utf-8') as hf:
            json.dump(history, hf, indent=2)
    except Exception:
        pass

    final_epoch = config['resume_epoch'] + config['num_epochs']
    if val_acc is not None:
        print(f"[Epoch {epoch_id + 1}/{final_epoch}] Saved to {checkpoint_path} | Val NLL: {val_nll:.4f} | Val Acc: {val_acc:.2f}%")
    else:
        print(f"[Epoch {epoch_id + 1}/{final_epoch}] Saved to {checkpoint_path}")

    return checkpoint_path

def resolve_test_epoch(config: dict, last_trained_epoch: typing.Optional[int] = None) -> int:
    """Select the checkpoint epoch evaluated by test()

    Priority:
        1. last_trained_epoch: the final epoch of a train() call made on the same object, so a
           train-then-test script always scores the model it has just trained (also after resuming)
        2. config['resume_epoch'] if > 0: an explicitly requested checkpoint
        3. the latest Epoch_*.pt found in config['logdir']

    Raises FileNotFoundError when no checkpoint can be found, instead of silently testing an
    untrained (randomly initialized) model.
    """
    if last_trained_epoch is not None and last_trained_epoch > 0:
        return last_trained_epoch

    resume_epoch = config.get('resume_epoch', 0) or 0
    if resume_epoch > 0:
        return resume_epoch

    checkpoints = list_epoch_checkpoints(logdir=config['logdir'])
    if len(checkpoints) == 0:
        raise FileNotFoundError('No checkpoint (Epoch_*.pt) found in {0:s}. Train a model first or set resume_epoch.'.format(config['logdir']))

    latest_epoch = max(checkpoints)
    print('resume_epoch is not set: testing the latest checkpoint Epoch_{0:d}.pt\n'.format(latest_epoch))
    return latest_epoch
```

**Differences from the branch's inline version** (§4.2.4 shows the branch code):

| Aspect | Branch (inline, committed) | Review (helpers) |
|---|---|---|
| `val_acc`/`val_nll` for an epoch without validation | `0.0` (or `None` in the checkpoint only) | `None` everywhere |
| Best-checkpoint selection | Any epoch could become best, including unvalidated `0.0` ones | Only validated epochs; `None` never wins |
| Fresh run (`resume_epoch = 0`) in a used `logdir` | Old history appended to; stale `Epoch_N.pt` from longer runs kept, so `"last"` could pick an old file | Old `Epoch_*.pt`, `best_model.pt`, `training_history.json` deleted first. TensorBoard event files and other files are kept |
| Validation frequency | Every epoch | `validate_every_n_epochs` (0 = off; final epoch always validated when > 0) |
| `test()` with `resume_epoch = 0` | Loaded `Epoch_{num_epochs}.pt` | Epoch just trained by the same object, else latest checkpoint, else clear error |

#### 4.1.5 New `FocalLoss`
**Origin:** [Branch] · ADDITIVE (used only by the tabular scripts)
```python
class FocalLoss(torch.nn.Module):
    """
    Multi-class / Binary Focal Loss implementation with class balancing weights.
    FL(p_t) = -alpha_t * (1 - p_t)^gamma * log(p_t)
    """
    def __init__(self, alpha: typing.Optional[typing.Union[float, torch.Tensor, typing.List[float]]] = 0.25, gamma: float = 2.0, reduction: str = 'mean') -> None:
        super(FocalLoss, self).__init__()
        if alpha is not None:
            if isinstance(alpha, (float, int)):
                # For binary classification: class 0 gets (1 - alpha), class 1 gets alpha
                alpha = torch.tensor([1.0 - float(alpha), float(alpha)], dtype=torch.float32)
            elif not isinstance(alpha, torch.Tensor):
                alpha = torch.tensor(alpha, dtype=torch.float32)
            self.register_buffer('alpha', alpha)
        else:
            self.alpha = None
        self.gamma = gamma
        self.reduction = reduction

    def forward(self, input: torch.Tensor, target: torch.Tensor) -> torch.Tensor:
        """
        Args:
            input: (N, C) logits
            target: (N,) ground truth class indices
        """
        log_p = torch.nn.functional.log_softmax(input, dim=-1)
        p = torch.exp(log_p)

        target_reshaped = target.view(-1, 1)
        log_pt = log_p.gather(dim=-1, index=target_reshaped).view(-1)
        pt = p.gather(dim=-1, index=target_reshaped).view(-1)

        focal_weight = (1.0 - pt) ** self.gamma
        loss = -focal_weight * log_pt

        if self.alpha is not None:
            alpha = self.alpha.to(input.device)
            alpha_t = alpha.gather(dim=0, index=target.view(-1))
            loss = alpha_t * loss

        if self.reduction == 'mean':
            return loss.mean()
        elif self.reduction == 'sum':
            return loss.sum()
        else:
            return loss
```

---

### 4.2 `MLBaseClass.py`

#### 4.2.1 Imports
**Origin:** [Branch → Review]. The branch added `import json`; the review removed it again (JSON handling moved to `_utils.py`) and added:
```python
from _utils import clear_run_artifacts, validation_due, save_epoch_checkpoint, resolve_test_epoch
```

#### 4.2.2 Fresh-run clean-up before training
**Origin:** [Review] · BEHAVIOUR (deletes files)

Inserted after `model["optimizer"].zero_grad()` in `train()`:
```python
        # a fresh run must not mix its checkpoints/history with those of an older run in the same logdir
        if self.config['resume_epoch'] == 0:
            clear_run_artifacts(logdir=self.config['logdir'])
```
> ⚠️ Retraining a model from scratch **deletes that `logdir`'s previous `Epoch_*.pt`, `best_model.pt` and `training_history.json`**. `*.pt` is git-ignored, so copy the folder first to keep an old model. Resumed runs (`resume_epoch > 0`) keep all files.

#### 4.2.3 `global_step` defined at the start of each epoch
**Origin:** [Branch] · BEHAVIOUR

Inserted at the top of the epoch loop:
```python
            for epoch_id in range(self.config['resume_epoch'], self.config['resume_epoch'] + self.config['num_epochs'], 1):
                loss_monitor = 0.
                global_step = (epoch_id + 1) * self.config['num_episodes_per_epoch'] // max(1, self.config['minibatch_print'])
```
This keeps `global_step` defined for end-of-epoch validation, even if no print step occurred during the epoch.

#### 4.2.4 Validation moved from inside the print block to the end of the epoch; checkpointing replaced
**Origin:** [Branch → Review] · BEHAVIOUR (no change to meta-updates)

**Before (`master`).** Validation ran **every `minibatch_print` episodes**, nested inside the monitoring block:
```python
                        # monitoring
                        if (eps_count + 1) % self.config['minibatch_print'] == 0:
                            loss_monitor = loss_monitor * self.config["minibatch"] / self.config["minibatch_print"]

                            # calculate step for Tensorboard Summary Writer
                            global_step = (epoch_id * self.config['num_episodes_per_epoch'] + eps_count + 1) // self.config['minibatch_print']

                            tb_writer.add_scalar(tag="Train_Loss", scalar_value=loss_monitor, global_step=global_step)

                            # reset monitoring variables
                            loss_monitor = 0.

                            # -------------------------
                            # Validation
                            # -------------------------
                            if val_dataloader is not None:
                                # turn on EVAL mode to disable dropout
                                model["f_base_net"].eval()

                                loss_temp, accuracy_temp = self.evaluate(
                                    num_eps=self.config['num_episodes'],
                                    eps_dataloader=val_dataloader,
                                    model=model
                                )

                                tb_writer.add_scalar(tag="Val_NLL", scalar_value=np.mean(loss_temp), global_step=global_step)
                                tb_writer.add_scalar(tag="Val_Accuracy", scalar_value=np.mean(accuracy_temp), global_step=global_step)

                                model["f_base_net"].train()
                                del loss_temp
                                del accuracy_temp

                # save model
                checkpoint = {
                    "hyper_net_state_dict": model["hyper_net"].state_dict(),
                    "opt_state_dict": model["optimizer"].state_dict()
                }
                checkpoint_path = os.path.join(self.config['logdir'], 'Epoch_{0:d}.pt'.format(epoch_id + 1))
                torch.save(obj=checkpoint, f=checkpoint_path)
                print('State dictionaries are saved into {0:s}\n'.format(checkpoint_path))
```

**Intermediate (branch, committed).** Validation ran once at the end of every epoch, followed by about 50 lines of inline checkpoint, history and best-model code:
```python
                            # reset monitoring variables
                            loss_monitor = 0.

                            pass

                # -------------------------
                # Validation (End of Epoch)
                # -------------------------
                val_nll_mean, val_acc_mean = 0.0, 0.0
                if val_dataloader is not None:
                    model["f_base_net"].eval()
                    loss_temp, accuracy_temp = self.evaluate(
                        num_eps=self.config['num_episodes'],
                        eps_dataloader=val_dataloader,
                        model=model
                    )
                    val_nll_mean = float(np.mean(loss_temp))
                    val_acc_mean = float(np.mean(accuracy_temp))
                    
                    # Log to TensorBoard using the last global_step of the epoch
                    tb_writer.add_scalar(tag="Val_NLL", scalar_value=val_nll_mean, global_step=global_step)
                    tb_writer.add_scalar(tag="Val_Accuracy", scalar_value=val_acc_mean, global_step=global_step)
                    model["f_base_net"].train()

                # save model with validation accuracy metadata
                checkpoint = {
                    "epoch": epoch_id + 1,
                    "hyper_net_state_dict": model["hyper_net"].state_dict(),
                    "opt_state_dict": model["optimizer"].state_dict(),
                    "val_acc": val_acc_mean if val_dataloader is not None else None,
                    "val_nll": val_nll_mean if val_dataloader is not None else None
                }
                checkpoint_path = os.path.join(self.config['logdir'], 'Epoch_{0:d}.pt'.format(epoch_id + 1))
                torch.save(obj=checkpoint, f=checkpoint_path)

                # Maintain training history & best checkpoint
                history_file = os.path.join(self.config['logdir'], 'training_history.json')
                history = {"epochs": [], "best_checkpoint": None}
                if os.path.exists(history_file):
                    try:
                        with open(history_file, 'r', encoding='utf-8') as hf:
                            history = json.load(hf)
                    except Exception:
                        history = {"epochs": [], "best_checkpoint": None}

                epoch_record = {
                    "epoch": epoch_id + 1,
                    "val_acc": val_acc_mean,
                    "val_nll": val_nll_mean,
                    "checkpoint_path": checkpoint_path
                }
                history["epochs"].append(epoch_record)

                curr_best = history.get("best_checkpoint")
                if curr_best is None or (val_acc_mean is not None and val_acc_mean >= curr_best.get("val_acc", -1)):
                    history["best_checkpoint"] = epoch_record
                    # Save best model copy
                    best_model_path = os.path.join(self.config['logdir'], 'best_model.pt')
                    torch.save(obj=checkpoint, f=best_model_path)

                try:
                    with open(history_file, 'w', encoding='utf-8') as hf:
                        json.dump(history, hf, indent=2)
                except Exception:
                    pass
                
                # --- Clean Console Logging ---
                if val_dataloader is not None:
                    print(f"[Epoch {epoch_id + 1}/{self.config['resume_epoch'] + self.config['num_epochs']}] Saved to {checkpoint_path} | Val NLL: {val_nll_mean:.4f} | Val Acc: {val_acc_mean:.2f}%")
                else:
                    print(f"[Epoch {epoch_id + 1}/{self.config['resume_epoch'] + self.config['num_epochs']}] Saved to {checkpoint_path}")
```

**After (review, current):**
```python
                            # reset monitoring variables
                            loss_monitor = 0.

                # -------------------------
                # Validation (end of epoch, every config['validate_every_n_epochs'] epochs)
                # -------------------------
                val_nll_mean, val_acc_mean = None, None
                if val_dataloader is not None and validation_due(config=self.config, epoch_id=epoch_id):
                    model["f_base_net"].eval()
                    loss_temp, accuracy_temp = self.evaluate(
                        num_eps=self.config['num_episodes'],
                        eps_dataloader=val_dataloader,
                        model=model
                    )
                    val_nll_mean = float(np.mean(loss_temp))
                    val_acc_mean = float(np.mean(accuracy_temp))

                    # Log to TensorBoard using the last global_step of the epoch
                    tb_writer.add_scalar(tag="Val_NLL", scalar_value=val_nll_mean, global_step=global_step)
                    tb_writer.add_scalar(tag="Val_Accuracy", scalar_value=val_acc_mean, global_step=global_step)
                    model["f_base_net"].train()

                # save Epoch_{N}.pt, training_history.json and best_model.pt
                save_epoch_checkpoint(model=model, config=self.config, epoch_id=epoch_id, val_nll=val_nll_mean, val_acc=val_acc_mean)
                self.last_trained_epoch = epoch_id + 1
```

**Effect:**
- Validation cost is now about 7.5–9.3 % of each epoch at the default `validate_every_n_epochs = 1` (measured from TensorBoard timestamps of the tabular runs). Master's schedule (4 validations per epoch with the tabular config) would cost about 27 %, an estimate of 4× the measured per-validation time. With `0`, validation is skipped entirely.
- TensorBoard `Val_*` curves have one point per validated epoch instead of one per `minibatch_print` episodes.
- Meta-updates are unaffected, because validation does no backward pass.
- Checkpoints gain the `epoch`, `val_acc` and `val_nll` keys. Old checkpoints still load, because loaders only read `hyper_net_state_dict` / `opt_state_dict`.

#### 4.2.5 `test()`: checkpoint resolution
**Origin:** [Branch → Review] · BEHAVIOUR

**Before (`master`)**, which tested a **randomly initialised model** when `resume_epoch = 0`:
```python
        model = self.load_model(resume_epoch=self.config["resume_epoch"], hyper_net_class=self.hyper_net_class, eps_dataloader=eps_dataloader)
```

**Intermediate (branch):**
```python
        epoch = self.config.get("resume_epoch", 0)
        if not epoch or epoch <= 0:
            epoch = self.config.get("num_epochs", 1)

        model = self.load_model(resume_epoch=epoch, hyper_net_class=self.hyper_net_class, eps_dataloader=eps_dataloader)
```

**After (review):**
```python
        epoch = resolve_test_epoch(config=self.config, last_trained_epoch=getattr(self, 'last_trained_epoch', None))

        model = self.load_model(resume_epoch=epoch, hyper_net_class=self.hyper_net_class, eps_dataloader=eps_dataloader)
```
The documented `main.py ... --resume-epoch N --test` usage behaves exactly as in `master`.

---

### 4.3 `Platipus.py`

`Platipus` does not inherit from `MLBaseClass` and has its own `train()`. It received the same changes as §4.2, plus two of its own.

#### 4.3.1 Imports
**Origin:** [Branch → Review]. The branch added `import json` (later removed). Current import line:
```python
# before
from _utils import kl_divergence_gaussians
# after
from _utils import kl_divergence_gaussians, clear_run_artifacts, validation_due, save_epoch_checkpoint, resolve_test_epoch
```

#### 4.3.2 Threshold accuracy
See §3.

#### 4.3.3 Fresh-run clean-up
Same as §4.2.2 (double quotes in this file):
```python
        # a fresh run must not mix its checkpoints/history with those of an older run in the same logdir
        if self.config["resume_epoch"] == 0:
            clear_run_artifacts(logdir=self.config["logdir"])
```

#### 4.3.4 `global_step` crash fix
**Origin:** [Review] · BEHAVIOUR (fixes a crash introduced on the branch)

The branch moved validation to the end of the epoch, but `global_step` was still assigned only inside the print block. Whenever no print step happened in the first epoch (`num_episodes_per_epoch < minibatch_print`, or a dataloader shorter than `minibatch_print`), validation raised `UnboundLocalError: local variable 'global_step' referenced before assignment`. This was reproduced on the committed branch in 2 of 4 SineLine runs.

Added at the start of each epoch:
```python
            for epoch_id in range(self.config["resume_epoch"], self.config["resume_epoch"] + self.config["num_epochs"], 1):
                loss_monitor = 0.
                # defined up-front so end-of-epoch validation can log even if no print step happened in this epoch
                global_step = (epoch_id + 1) * self.config["num_episodes_per_epoch"] // max(1, self.config["minibatch_print"])
```

#### 4.3.5 Validation and checkpointing
**Before (`master`)**: validation inside the print block, **without** `eval()`/`train()` toggling:
```python
                            # reset monitoring variables
                            loss_monitor = 0.

                            # -------------------------
                            # Validation
                            # -------------------------
                            if val_dataloader is not None:
                                loss_temp, accuracy_temp = self.evaluate(
                                    num_eps=self.config['num_episodes'],
                                    eps_dataloader=val_dataloader,
                                    model=model
                                )

                                tb_writer.add_scalar(tag="Val_NLL", scalar_value=np.mean(loss_temp), global_step=global_step)
                                tb_writer.add_scalar(tag="Val_Accuracy", scalar_value=np.mean(accuracy_temp), global_step=global_step)

                                del loss_temp
                                del accuracy_temp

                # save model
                checkpoint = {
                    "hyper_net_state_dict": model["hyper_net"].state_dict(),
                    "opt_state_dict": model["optimizer"].state_dict()
                }
                checkpoint_path = os.path.join(self.config["logdir"], "Epoch_{0:d}.pt".format(epoch_id + 1))
                torch.save(obj=checkpoint, f=checkpoint_path)
                print("State dictionaries are saved into {0:s}\n".format(checkpoint_path))
```

**Intermediate (branch):** identical to the branch code shown in §4.2.4, plus `model["f_base_net"].eval()` / `.train()` around validation (new for PLATIPUS).

**After (review):**
```python
                            # reset monitoring variables
                            loss_monitor = 0.

                # -------------------------
                # Validation (end of epoch, every config['validate_every_n_epochs'] epochs)
                # -------------------------
                val_nll_mean, val_acc_mean = None, None
                if val_dataloader is not None and validation_due(config=self.config, epoch_id=epoch_id):
                    model["f_base_net"].eval()
                    loss_temp, accuracy_temp = self.evaluate(
                        num_eps=self.config['num_episodes'],
                        eps_dataloader=val_dataloader,
                        model=model
                    )
                    val_nll_mean = float(np.mean(loss_temp))
                    val_acc_mean = float(np.mean(accuracy_temp))

                    # Log to TensorBoard using the last global_step of the epoch
                    tb_writer.add_scalar(tag="Val_NLL", scalar_value=val_nll_mean, global_step=global_step)
                    tb_writer.add_scalar(tag="Val_Accuracy", scalar_value=val_acc_mean, global_step=global_step)
                    model["f_base_net"].train()

                # save Epoch_{N}.pt, training_history.json and best_model.pt
                save_epoch_checkpoint(model=model, config=self.config, epoch_id=epoch_id, val_nll=val_nll_mean, val_acc=val_acc_mean)
                self.last_trained_epoch = epoch_id + 1
```

#### 4.3.6 `test()` implemented
**Origin:** [Branch → Review] · ADDITIVE

**Before (`master`)**: commented out and non-functional. It referenced the undefined names `eps_generator` and `get_episodes`.
```python
    # def test(self, num_eps: int, eps_dataloader: torch.utils.data.DataLoader) -> None:
    #     """Evaluate the performance
    #     """
    #     print("Evaluation is started.\n")

    #     model = self.load_model(resume_epoch=self.config["resume_epoch"], hyper_net_class=self.hyper_net_class, eps_generator=eps_generator)

    #     # get list of episode names, each episode name consists of classes
    #     eps = get_episodes(episode_file_path=self.config["episode_file"])

    #     _, accuracy = self.evaluate(eps=eps, eps_generator=eps_generator, model=model)

    #     print("Accuracy = {0:.2f} +/- {1:.2f}\n".format(np.mean(accuracy), 1.96 * np.std(accuracy) / np.sqrt(len(accuracy))))
    #     return None
```

**Intermediate (branch):** implemented with `epoch = resume_epoch or num_epochs (default 20)`.

**After (review):**
```python
    def test(self, num_eps: int, eps_dataloader: torch.utils.data.DataLoader) -> None:
        """Evaluate the performance
        """
        print("Evaluation is started.\n")

        epoch = resolve_test_epoch(config=self.config, last_trained_epoch=getattr(self, 'last_trained_epoch', None))

        model = self.load_model(resume_epoch=epoch, hyper_net_class=self.hyper_net_class, eps_dataloader=eps_dataloader)

        loss, accuracy = self.evaluate(num_eps=num_eps, eps_dataloader=eps_dataloader, model=model)

        print('NLL = {0} +/- {1}'.format(np.mean(loss), 1.96 * np.std(loss) / np.sqrt(len(loss))))
        print("Accuracy = {0:.2f} +/- {1:.2f}\n".format(np.mean(accuracy), 1.96 * np.std(accuracy) / np.sqrt(len(accuracy))))
        return None
```

---

### 4.4 `Maml.py`

`Maml.load_model` builds the base network for **MAML, VAMPIRE, ABML, BMAML and PLATIPUS** (the others call it), so these changes apply to all five.

#### 4.4.1 Imports
**Origin:** [Branch] · ADDITIVE
```python
# before
from CommonModels import CNN, ResNet18, FcNet, ResNet10
# after
from CommonModels import CNN, ResNet18, FcNet, ResNet10, LogisticRegression
```

#### 4.4.2 Configurable `FcNet`
**Origin:** [Branch] · ADDITIVE (defaults reproduce master)
```python
# before
            base_net = FcNet(
                dim_output=self.config['num_ways'],
                num_hidden_units=(40, 40)
            )
# after
            base_net = FcNet(
                dim_output=self.config['num_ways'],
                num_hidden_units=self.config.get('num_hidden_units', (40, 40)),
                activation=self.config.get('activation', 'relu'),
                dropout_rate=self.config.get('dropout_rate', 0.0),
                use_layernorm=self.config.get('use_layernorm', False)
            )
```
`main.py` never sets these keys, so `main.py` runs build exactly master's 40×40 ReLU network.

#### 4.4.3 `LogisticRegression` backbone
**Origin:** [Branch] · ADDITIVE
```python
        elif self.config['network_architecture'] == 'LogisticRegression':
            base_net = LogisticRegression(
                dim_output=self.config['num_ways']
            )
```

#### 4.4.4 Device placement before lazy-module initialisation
**Origin:** [Branch] · COMPAT (fixes GPU device mismatch; weights identical)
```python
# inserted before the dummy forward pass
        # move to device before dummy forward to ensure LazyLinear initializes on the correct device
        base_net.to(self.config['device'])

# before
            base_net.forward(split_data['x_t'])
# after
            base_net.forward(split_data['x_t'].to(self.config['device']))

# comment change
-        # move to device
+        # move hyper_net to device
```

#### 4.4.5 Threshold accuracy
See §3.

---

### 4.5 `Abml.py`

#### 4.5.1 Prior hyper-parameters from config
**Origin:** [Branch] · ADDITIVE (defaults equal master)
```python
# before
        self.gamma_prior = torch.distributions.gamma.Gamma(concentration=1, rate=0.01)
        self.normal_prior = torch.distributions.normal.Normal(loc=0, scale=1)
# after
        concentration = float(self.config.get('gamma_prior_concentration', 1.0))
        rate = float(self.config.get('gamma_prior_rate', 0.01))
        norm_loc = float(self.config.get('normal_prior_loc', 0.0))
        norm_scale = float(self.config.get('normal_prior_scale', 1.0))

        self.gamma_prior = torch.distributions.gamma.Gamma(concentration=concentration, rate=rate)
        self.normal_prior = torch.distributions.normal.Normal(loc=norm_loc, scale=norm_scale)
```

#### 4.5.2 Threshold accuracy
See §3.

---

### 4.6 `Bmaml.py`

#### 4.6.1 SVGD repulsive weight
**Origin:** [Branch] · ADDITIVE (default 1.0 = master)
```python
# before
            q_params = q_params - self.config["inner_lr"] * (torch.matmul(kernel_matrix, distance_NLL) - grad_kernel)
# after
            repulsive_weight = float(self.config.get('svgd_repulsive_weight', 1.0))
            q_params = q_params - self.config["inner_lr"] * (torch.matmul(kernel_matrix, distance_NLL) - repulsive_weight * grad_kernel)
```

#### 4.6.2 Threshold accuracy
See §3.

#### 4.6.3 Kernel bandwidth (`get_kernel`)
**Origin:** [Branch → Review] · default behaviour **identical to master** (verified for M = 2, 3, 8)

**Before (`master`):**
```python
        pairwise_d_matrix = self.get_pairwise_distance_matrix(x=params)

        median_dist = torch.quantile(input=pairwise_d_matrix, q=0.5)  # tf.reduce_mean(euclidean_dists) ** 2
        h = median_dist / np.log(self.config["num_models"])

        kernel_matrix = torch.exp(-pairwise_d_matrix / h)
```

**Intermediate (branch, committed).** Median taken over off-diagonal entries only, so the bandwidth doubled at M = 2:
```python
        pairwise_d_matrix = self.get_pairwise_distance_matrix(x=params)

        # Compute median over off-diagonal non-zero distances to avoid zero collapse
        off_diag_dists = pairwise_d_matrix[pairwise_d_matrix > 0]
        if off_diag_dists.numel() > 0:
            median_dist = torch.quantile(input=off_diag_dists, q=0.5)
        else:
            median_dist = torch.tensor(1.0, device=params.device)

        bandwidth_scale = float(self.config.get('svgd_bandwidth_scale', 1.0))
        h = (median_dist / np.log(max(2, self.config["num_models"]))) * bandwidth_scale
        h = torch.clamp(h, min=1e-5)

        kernel_matrix = torch.exp(-pairwise_d_matrix / h)
```

**After (review): restored to master, keeping two safe additions:**
```python
        pairwise_d_matrix = self.get_pairwise_distance_matrix(x=params)

        # median over the full N x N matrix (zero diagonal included), as in master and the original BMAML 'org' kernel
        median_dist = torch.quantile(input=pairwise_d_matrix, q=0.5)  # tf.reduce_mean(euclidean_dists) ** 2
        h = median_dist / np.log(self.config["num_models"])

        # svgd_bandwidth_scale = 1.0 (default) keeps the bandwidth of master
        h = h * float(self.config.get('svgd_bandwidth_scale', 1.0))

        # safety floor only: if all particles collapse, median_dist = 0 and exp(-0 / 0) would give NaN
        h = torch.clamp(h, min=1e-5)

        kernel_matrix = torch.exp(-pairwise_d_matrix / h)
```

**Background and evidence:**
- The full-matrix median (zero diagonal included) is the default `'org'` kernel of the original BMAML code (`jaesik817/bmaml`, `bmaml.py::kernel`). The off-diagonal median matches the SVGD paper's wording and BMAML's optional `'med'` kernel. Both are valid heuristics.
- Measured on the tabular models at epoch 100 (8 particles): FcNet bandwidth differs by 0.4 %, LogReg by 13.8 %.
- In a 6-epoch, 2-seed comparison on both backbones, the branch version and master's gave **identical** results to 3–4 decimals. Original BMAML with squared distances (both `'org'` and `'med'`) was ≤ 0.003 PR-AUC better, which is within noise. See §10.

---

### 4.7 `Vampire2.py`

#### 4.7.1 Threshold accuracy
See §3.

#### 4.7.2 Exact KL divergence to N(0, I)
**Origin:** [Branch] (fix), [Review] (documentation) · loss value only, **gradients unchanged**
```python
# before
    @staticmethod
    def KL_divergence_standard_normal(p: typing.List[torch.Tensor]) -> typing.Union[torch.Tensor, float]:
        """Calculate KL divergence between a diagonal Gaussian with N(0, I)
        """
        KL_div = 0

        n = len(p) // 2

        for i in range(n):
            p_mean = p[i]
            p_log_std = p[n + i]

            KL_div = KL_div + torch.sum(input=torch.square(input=p_mean))
            KL_div = KL_div + torch.sum(input=torch.exp(input=2 * p_log_std))
            KL_div = KL_div - n
            KL_div = KL_div - 2 * torch.sum(input=p_log_std)

        KL_div = KL_div / 2

        return KL_div

# after
    @staticmethod
    def KL_divergence_standard_normal(p: typing.List[torch.Tensor]) -> typing.Union[torch.Tensor, float]:
        """Calculate KL divergence between a diagonal Gaussian with N(0, I)

        KL = 1/2 * sum_j (mu_j^2 + sigma_j^2 - 1 - 2 * log sigma_j), summed over every element j.

        Note: master subtracted n (= number of parameter tensors) instead of numel(p_mean) per tensor.
        That only shifts KL by a constant, so the gradients used to train VAMPIRE are unchanged;
        only the reported loss value now equals the exact KL.
        """
        KL_div = 0

        n = len(p) // 2

        for i in range(n):
            p_mean = p[i]
            p_log_std = p[n + i]

            KL_div = KL_div + torch.sum(input=torch.square(input=p_mean))
            KL_div = KL_div + torch.sum(input=torch.exp(input=2 * p_log_std))
            KL_div = KL_div - torch.numel(p_mean) # the "-1" term, once per element (master: "- n")
            KL_div = KL_div - 2 * torch.sum(input=p_log_std)

        KL_div = KL_div / 2

        return KL_div
```
The exact KL is $\tfrac12\sum_j(\mu_j^2+\sigma_j^2-1-2\log\sigma_j)$, where the constant is −1 **per element**. Master subtracted `n` (the number of tensors) once per tensor. VAMPIRE adds `KL_weight * KL` linearly to the loss, so a constant offset doesn't change any gradient.

---

### 4.8 `Simpa.py`

#### 4.8.1 Imports and architecture dictionary
**Origin:** [Branch] · ADDITIVE
```python
# before
from CommonModels import CNN, FcNet
# after
from CommonModels import CNN, FcNet, LogisticRegression
```
```python
# before
        if self.config['network_architecture'] == 'FcNet':
            base_net = FcNet(
                dim_output=self.config['num_ways'],
                num_hidden_units=(40, 40)
            )
        elif self.config['network_architecture'] == 'CNN':
            base_net = CNN(
                dim_output=self.config['num_ways'],
                bn_affine=self.config['batchnorm'],
                stride_flag=self.config['strided']
            )
        else:
            raise NotImplementedError('Network architecture is unknown. Please implement it in the CommonModels.py.')

# after
        network_architectures = {
            'FcNet': lambda: FcNet(
                dim_output=self.config['num_ways'],
                num_hidden_units=self.config.get('num_hidden_units', (40, 40)),
                activation=self.config.get('activation', 'relu'),
                dropout_rate=self.config.get('dropout_rate', 0.0),
                use_layernorm=self.config.get('use_layernorm', False)
            ),
            'CNN': lambda: CNN(dim_output=self.config['num_ways'], bn_affine=self.config['batchnorm'], stride_flag=self.config['strided']),
            'LogisticRegression': lambda: LogisticRegression(dim_output=self.config['num_ways'])
        }

        if self.config['network_architecture'] not in network_architectures:
            raise NotImplementedError(
                'Network architecture is unknown. Please implement it in the CommonModels.py.')

        base_net = network_architectures[self.config['network_architecture']]()
```

#### 4.8.2 Exact KL divergence
**Origin:** [Branch] (fix), [Review] (documentation) · **TRAINING**

Same code change as §4.7.2 (`KL_div - n` → `KL_div - torch.numel(p_mean)`), with this docstring:
```python
        """Calculate KL divergence between a diagonal Gaussian with N(0, I)

        KL = 1/2 * sum_j (mu_j^2 + sigma_j^2 - 1 - 2 * log sigma_j), summed over every element j.

        Note: master subtracted n (= number of parameter tensors) instead of numel(p_mean) per tensor,
        which over-estimated KL by a constant. Because SIMPA uses KL inside sqrt(...) of the PAC-Bayes
        bound, this constant also scaled the gradients; SIMPA now trains with the exact KL, so its
        results are not bit-comparable with runs made on master.
        """
```
SIMPA uses KL inside `torch.sqrt(...)` of the PAC-Bayes bound (lines ~436, ~452, ~610), and the gradient of $\sqrt{K+c}$ depends on $c$. SIMPA training therefore differs from `master`, now using the correct KL.

#### 4.8.3 Formatting
**Origin:** [Branch] · COSMETIC. Blank lines added between top-level definitions; two blank lines removed inside the training loop.

#### 4.8.4 Not changed
`Simpa.train` keeps its own upstream loop. It saves only `Epoch_{N}.pt` and does **not** write `training_history.json` or `best_model.pt` (documented as an exemption in `AGENTS.md`).

---

### 4.9 `ProtoNet.py`

#### 4.9.1 Imports
**Origin:** [Branch → Review]. The branch added an `FcNet` option but forgot the import, so selecting it raised `NameError`. The review added the import.
```python
# master
from CommonModels import CNN, ResNet18
# branch (committed)
from CommonModels import CNN, ResNet18, LogisticRegression
# current
from CommonModels import CNN, ResNet18, FcNet, LogisticRegression
```

#### 4.9.2 Architecture dictionary, device placement, split function
**Origin:** [Branch] · ADDITIVE, COMPAT
```python
# before (master)
        if self.config['network_architecture'] == 'CNN':
            model["hyper_net"] = CNN(
                dim_output=None,
                bn_affine=self.config['batchnorm'],
                stride_flag=self.config['strided']
            )
        elif self.config['network_architecture'] == 'ResNet18':
            model["hyper_net"] = ResNet18(
                dim_output=None,
                bn_affine=self.config['batchnorm']
            )
        else:
            raise NotImplementedError('Network architecture is unknown. Please implement it in the CommonModels.py.')

        # ---------------------------------------------------------------
        # run a dummy task to initialize lazy modules defined in base_net
        # ---------------------------------------------------------------
        for eps_data in eps_dataloader:
            # split data into train and validation
            split_data = train_val_split(eps_data=eps_data, k_shot=self.config['k_shot'])

            # run to initialize lazy modules
            model["hyper_net"].forward(split_data['x_t'])
            break

        params = torch.nn.utils.parameters_to_vector(parameters=model["hyper_net"].parameters())
        print('Number of parameters of the base network = {0:,}.\n'.format(params.numel()))

        # move to device
        model["hyper_net"].to(self.config['device'])

# after
        network_architectures = {
            'FcNet': lambda: FcNet(
                dim_output=None,
                num_hidden_units=self.config.get('num_hidden_units', (40, 40)),
                activation=self.config.get('activation', 'relu'),
                dropout_rate=self.config.get('dropout_rate', 0.0),
                use_layernorm=self.config.get('use_layernorm', False)
            ),
            'CNN': lambda: CNN(dim_output=None, bn_affine=self.config['batchnorm'], stride_flag=self.config['strided']),
            'ResNet18': lambda: ResNet18(dim_output=None, bn_affine=self.config['batchnorm']),
            'LogisticRegression': lambda: LogisticRegression(dim_output=None)
        }

        if self.config['network_architecture'] not in network_architectures:
            raise NotImplementedError(
                'Network architecture is unknown. Please implement it in the CommonModels.py.')

        model['hyper_net'] = network_architectures[self.config['network_architecture']]()

        # move to device before dummy forward to ensure LazyLinear initializes on the correct device
        model['hyper_net'].to(self.config['device'])

        # ---------------------------------------------------------------
        # run a dummy task to initialize lazy modules defined in base_net
        # ---------------------------------------------------------------
        split_fn = self.config.get('train_val_split_function', train_val_split)
        for eps_data in eps_dataloader:
            # split data into train and validation
            split_data = split_fn(eps_data=eps_data, k_shot=self.config['k_shot'])

            # run to initialize lazy modules
            model["hyper_net"].forward(split_data['x_t'].to(self.config['device']))
            break

        params = torch.nn.utils.parameters_to_vector(parameters=model["hyper_net"].parameters())
        print('Number of parameters of the base network = {0:,}.\n'.format(params.numel()))
```
ProtoNet still uses `argmax` accuracy and `torch.nn.functional.cross_entropy` (unchanged).

---

### 4.10 `HyperNetClasses.py`

#### 4.10.1 Imports
```python
# before
from _utils import intialize_parameters, vector_to_list_parameters
# after                                                      [Review]
from _utils import intialize_parameters, layernorm_weight_keys, vector_to_list_parameters
```

#### 4.10.2 LayerNorm-aware initialisation in all four hyper-nets
**Origin:** [Review] · TRAINING only with LayerNorm (see §4.1.3)
```python
# IdentityNet (MAML)
-        params = intialize_parameters(state_dict=base_state_dict)
+        params = intialize_parameters(state_dict=base_state_dict, ones_keys=layernorm_weight_keys(module=base_net))

# NormalVariationalNet (VAMPIRE, ABML)
-        mean = intialize_parameters(state_dict=base_state_dict)
+        mean = intialize_parameters(state_dict=base_state_dict, ones_keys=layernorm_weight_keys(module=base_net))

# EnsembleNet (BMAML)
-            params_list = intialize_parameters(state_dict=base_state_dict) # list of tensors
+            params_list = intialize_parameters(state_dict=base_state_dict, ones_keys=layernorm_weight_keys(module=base_net)) # list of tensors
```

#### 4.10.3 `ParameterList` API update
**Origin:** [Branch] · COMPAT

Current PyTorch removed the `parameters=` keyword of `ParameterList.__init__` and renamed `append`'s argument. Unmodified `master` fails at start-up on this environment with `TypeError: ParameterList.__init__() got an unexpected keyword argument 'parameters'` (verified for PLATIPUS).
```python
# EnsembleNet
-        self.params = torch.nn.ParameterList(parameters=None) # empty parameter list
+        self.params = torch.nn.ParameterList() # empty parameter list
-            self.params.append(parameter=torch.nn.Parameter(data=params_vec))
+            self.params.append(torch.nn.Parameter(data=params_vec))
```

#### 4.10.4 `PlatipusNet`: `mu_theta` initialisation
**Origin:** [Branch] (change), [Review] (LayerNorm keys, comment) · **TRAINING**

**Before (`master`):**
```python
        # initialize ParameterList
        self.params = torch.nn.ParameterList(parameters=None)

        # add parameters into ParameterList
        self.params.append(parameter=torch.nn.Parameter(data=torch.randn(size=(self.num_base_params,))))
        self.params.append(parameter=torch.nn.Parameter(data=torch.randn(size=(self.num_base_params,)) - 4))
        self.params.append(parameter=torch.nn.Parameter(data=torch.randn(size=(self.num_base_params,)) - 4))
        # for _ in ("mu_theta", "log_sigma_theta", "log_v_q"):
        #     params_list = intialize_parameters(state_dict=base_state_dict)
        #     params_vec = torch.nn.utils.parameters_to_vector(parameters=params_list) - 4 # flattened tensor
        #     self.params.append(parameter=torch.nn.Parameter(data=params_vec))
        
        self.params.append(parameter=torch.nn.Parameter(data=torch.tensor(0.01))) # gamma_p
        self.params.append(parameter=torch.nn.Parameter(data=torch.tensor(0.01))) # gamma_q
```

**After:**
```python
        # initialize ParameterList
        self.params = torch.nn.ParameterList()

        # add parameters into ParameterList
        # mu_theta: same initialization as the other hyper-nets (Kaiming-normal weights, zero biases).
        # master used torch.randn (N(0, 1)); on the tabular data that saturates the logits of the
        # 64x64 FcNet at initialization and gave a 45-85x higher validation loss after 6 epochs.
        params_list = intialize_parameters(state_dict=base_state_dict, ones_keys=layernorm_weight_keys(module=base_net))
        params_vec = torch.nn.utils.parameters_to_vector(parameters=params_list)
        self.params.append(torch.nn.Parameter(data=params_vec))
        self.params.append(torch.nn.Parameter(data=torch.randn(size=(self.num_base_params,)) - 4))
        self.params.append(torch.nn.Parameter(data=torch.randn(size=(self.num_base_params,)) - 4))
        
        self.params.append(torch.nn.Parameter(data=torch.tensor(0.01))) # gamma_p
        self.params.append(torch.nn.Parameter(data=torch.tensor(0.01))) # gamma_q
```
`log_sigma_theta`, `log_v_q`, `gamma_p` and `gamma_q` are initialised exactly as in master. Only `mu_theta` changes, from N(0, 1) to the Kaiming/zeros scheme that master uses for MAML, VAMPIRE, ABML and BMAML. The evidence for keeping it is in §2 and §10.

---

### 4.11 `CommonModels.py`

#### 4.11.1 `FcNet` rewritten
**Origin:** [Branch], [Review] (activation alias) · ADDITIVE (defaults reproduce master's layers)

**Before (`master`), complete class:**
```python
class FcNet(torch.nn.Module):
    """Simple fully connected network
    """
    def __init__(self, dim_output: typing.Optional[int] = None, num_hidden_units: typing.List[int] = (32, 32)) -> None:
        """
        Args:

        """
        super(FcNet, self).__init__()

        self.dim_output = dim_output
        self.num_hidden_units = num_hidden_units

        self.fc_net = self.construct_network()

    def construct_network(self):
        """
        """
        net = torch.nn.Sequential()
        net.add_module(
            name='layer0',
            module=torch.nn.Sequential(
                torch.nn.LazyLinear(out_features=self.num_hidden_units[0]),
                torch.nn.ReLU()
            )
        )

        for i in range(1, len(self.num_hidden_units)):
            net.add_module(
                name='layer{0:d}'.format(i),
                module=torch.nn.Sequential(
                    torch.nn.Linear(in_features=self.num_hidden_units[i - 1], out_features=self.num_hidden_units[i]),
                    torch.nn.ReLU()
                )
            )
        
        net.add_module(
            name='classifier',
            module=torch.nn.Linear(in_features=self.num_hidden_units[-1], out_features=self.dim_output) if self.dim_output is not None \
                else torch.nn.Identity()
        )

        return net

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """"""
        return self.fc_net(x)
```

**After, complete class:**
```python
class FcNet(torch.nn.Module):
    """Simple fully connected network with customizable topology, activations, and regularization."""
    def __init__(
        self,
        dim_output: typing.Optional[int] = None,
        num_hidden_units: typing.Union[typing.List[int], typing.Tuple[int, ...]] = (40, 40),
        activation: str = "relu",
        dropout_rate: float = 0.0,
        use_layernorm: bool = False
    ) -> None:
        super(FcNet, self).__init__()

        self.dim_output = dim_output
        self.num_hidden_units = list(num_hidden_units)
        self.activation = activation
        self.dropout_rate = float(dropout_rate)
        self.use_layernorm = bool(use_layernorm)

        self.fc_net = self.construct_network()

    def _get_activation(self) -> torch.nn.Module:
        act = str(self.activation).strip().lower()
        if act in ("leaky_relu", "leaky"):
            return torch.nn.LeakyReLU()
        elif act == "elu":
            return torch.nn.ELU()
        elif act == "gelu":
            return torch.nn.GELU()
        elif act == "tanh":
            return torch.nn.Tanh()
        return torch.nn.ReLU()

    def construct_network(self):
        net = torch.nn.Sequential()
        if len(self.num_hidden_units) == 0:
            net.add_module(
                name='classifier',
                module=torch.nn.LazyLinear(out_features=self.dim_output) if self.dim_output is not None else torch.nn.Identity()
            )
            return net

        # Layer 0
        l0_modules = [torch.nn.LazyLinear(out_features=self.num_hidden_units[0])]
        if self.use_layernorm:
            l0_modules.append(torch.nn.LayerNorm(self.num_hidden_units[0]))
        l0_modules.append(self._get_activation())
        if self.dropout_rate > 0.0:
            l0_modules.append(torch.nn.Dropout(p=self.dropout_rate))
        net.add_module(name='layer0', module=torch.nn.Sequential(*l0_modules))

        # Subsequent hidden layers
        for i in range(1, len(self.num_hidden_units)):
            li_modules = [torch.nn.Linear(in_features=self.num_hidden_units[i - 1], out_features=self.num_hidden_units[i])]
            if self.use_layernorm:
                li_modules.append(torch.nn.LayerNorm(self.num_hidden_units[i]))
            li_modules.append(self._get_activation())
            if self.dropout_rate > 0.0:
                li_modules.append(torch.nn.Dropout(p=self.dropout_rate))
            net.add_module(name='layer{0:d}'.format(i), module=torch.nn.Sequential(*li_modules))
        
        # Classifier output layer
        net.add_module(
            name='classifier',
            module=torch.nn.Linear(in_features=self.num_hidden_units[-1], out_features=self.dim_output) if self.dim_output is not None \
                else torch.nn.Identity()
        )

        return net

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.fc_net(x)
```

| Aspect | Master | Now |
|---|---|---|
| Default `num_hidden_units` | `(32, 32)` | `(40, 40)`. Every caller passes it explicitly, so there's no effect |
| Activation | ReLU | `relu` (default), `leaky_relu`/`leaky`, `elu`, `gelu`, `tanh` |
| Dropout | — | Optional (`dropout_rate > 0`) |
| LayerNorm | — | Optional (`use_layernorm`), placed after each linear layer, before the activation |
| No hidden layers | Crashed (`num_hidden_units[0]`) | A single `LazyLinear` classifier |
| Module names | `layer0`, `layer1`, `classifier` | Same, so `state_dict` keys of the default network are unchanged |

**Review change:** the alias `"leakyReLU"` was removed from `("leaky_relu", "leakyReLU", "leaky")`. The string is lower-cased first, so that entry could never match; behaviour is unchanged.

#### 4.11.2 torchvision API compatibility (ResNet10, ResNet18)
**Origin:** [Branch] · COMPAT
```python
# ResNet10, before
        net = torchvision.models.resnet._resnet(arch="resnet10", block=torchvision.models.resnet.BasicBlock, layers=[1, 1, 1, 1], pretrained=False, progress=False)
# ResNet10, after
        try:
            net = torchvision.models.resnet._resnet(
                weights=None,
                progress=False,
                block=torchvision.models.resnet.BasicBlock,
                layers=[1, 1, 1, 1]
            )
        except TypeError:
            net = torchvision.models.resnet._resnet(
                arch="resnet10",
                block=torchvision.models.resnet.BasicBlock,
                layers=[1, 1, 1, 1],
                pretrained=False,
                progress=False
            )

# ResNet18, before
        net = resnet18(pretrained=False)
# ResNet18, after
        try:
            net = resnet18(weights=None)
        except TypeError:
            net = resnet18(pretrained=False)
```
torchvision ≥ 0.13 replaced `pretrained`/`arch` with `weights`. The same randomly initialised architecture is built either way.

#### 4.11.3 New `LogisticRegression`
**Origin:** [Branch] · ADDITIVE
```python
class LogisticRegression(torch.nn.Module):
    def __init__(self, dim_output: typing.Optional[int] = None, bias: bool = True, **kwargs) -> None:
        super(LogisticRegression, self).__init__()

        self.dim_output = dim_output
        self.bias = bias

        self.net = self.construct_network()

    def construct_network(self) -> torch.nn.Module:
        net = torch.nn.Sequential()
        net.add_module(name='flatten', module=torch.nn.Flatten(
            start_dim=1, end_dim=-1))
        net.add_module(
            name='classifier',
            module=torch.nn.LazyLinear(out_features=self.dim_output, bias=self.bias) if self.dim_output is not None
            else torch.nn.Identity()
        )
        return net

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)
```
This is a single linear layer: 31 × 2 + 2 = 64 parameters on the tabular data.

---

### 4.12 `EpisodeSampler.py`

**Origin:** [Branch → Review] · BEHAVIOUR (bug fixes). Used only by `main.py` for image datasets.

**Before (`master`), complete `__init__`, `__iter__` and `__len__`:**
```python
    def __init__(self, sampler: torch.utils.data.Sampler[typing.List[int]], num_ways: int, num_samples_per_class: int, drop_last: bool = True) -> None:
        super().__init__(sampler=sampler, batch_size=num_ways, drop_last=drop_last)
        
        self.num_ways = num_ways
        self.num_samples_per_class = num_samples_per_class
        
        # create a list of dictionary, each has:
        # - key = label
        # - value = img_idx
        self.class_img_idx = [None] * len(sampler.data_source.datasets)
        j = 0 # track the length of each dataset
        for dataset_id in range(len(sampler.data_source.datasets)):
            self.class_img_idx[dataset_id] = {}
            for i in range(len(self.sampler.data_source.datasets[dataset_id])):
                label_idx = self.sampler.data_source.datasets[dataset_id].targets[i]
                if label_idx not in self.class_img_idx[dataset_id]:
                    self.class_img_idx[dataset_id][label_idx] = []
                self.class_img_idx[dataset_id][label_idx].append(i + j)
            j = len(sampler.data_source.datasets[dataset_id])
        

    def __iter__(self) -> typing.Iterator[typing.List[int]]:
        while(True):
            # randomly sample a dataset
            dataset_id = random.randint(a=0, b=len(self.sampler.data_source.datasets) - 1)

            # n-way
            labels = random.sample(population=self.class_img_idx[dataset_id].keys(), k=self.num_ways)

            # variable to store img idx
            batch = []

            for label in labels:
                batch.extend(random.sample(population=self.class_img_idx[dataset_id][label], k=self.num_samples_per_class))

            yield batch
            batch = []
            labels = []
    
    def __len__(self) -> int:
        return comb(N=len(self.label_list), k=self.batch)
```

**Intermediate (branch).** Included all of the fixes below except the start-up validation. A class with too few samples was sampled **with replacement**:
```python
            for label in labels:
                samples = self.class_img_idx[dataset_id][label]
                if len(samples) >= self.num_samples_per_class:
                    batch.extend(random.sample(population=samples, k=self.num_samples_per_class))
                else:
                    batch.extend(random.choices(population=samples, k=self.num_samples_per_class))
```

**After (current), complete:**
```python
    def __init__(self, sampler: torch.utils.data.Sampler[typing.List[int]], num_ways: int, num_samples_per_class: int, drop_last: bool = True) -> None:
        super().__init__(sampler=sampler, batch_size=num_ways, drop_last=drop_last)
        
        self.num_ways = num_ways
        self.num_samples_per_class = num_samples_per_class
        
        # Handle both single Dataset and ConcatDataset
        datasets = getattr(sampler.data_source, 'datasets', [sampler.data_source])
        self.class_img_idx = [None] * len(datasets)
        j = 0  # track the cumulative length of datasets
        for dataset_id, ds in enumerate(datasets):
            self.class_img_idx[dataset_id] = {}
            targets = getattr(ds, 'targets', None)
            for i in range(len(ds)):
                label_idx = targets[i] if targets is not None else ds[i][1]
                if isinstance(label_idx, torch.Tensor):
                    label_idx = label_idx.item()
                if label_idx not in self.class_img_idx[dataset_id]:
                    self.class_img_idx[dataset_id][label_idx] = []
                self.class_img_idx[dataset_id][label_idx].append(i + j)
            j += len(ds)

        # datasets with fewer than num_ways classes are skipped in __iter__, so at least one must be usable
        # (otherwise __iter__ would loop forever)
        usable_class_dicts = [class_dict for class_dict in self.class_img_idx if len(class_dict) >= self.num_ways]
        if len(usable_class_dicts) == 0:
            raise ValueError('No dataset has at least num_ways = {0:d} classes.'.format(self.num_ways))

        # as in master, samples are drawn without replacement: a class with too few samples is an error,
        # since repeating samples could put the same sample in both the support and the query set
        for class_dict in usable_class_dicts:
            for label, samples in class_dict.items():
                if len(samples) < self.num_samples_per_class:
                    raise ValueError(
                        'Class {0} has {1:d} samples, fewer than the {2:d} needed per class (k_shot + v_shot).'.format(
                            label, len(samples), self.num_samples_per_class
                        )
                    )

    def __iter__(self) -> typing.Iterator[typing.List[int]]:
        num_datasets = len(self.class_img_idx)
        while True:
            # randomly sample a dataset
            dataset_id = random.randint(a=0, b=num_datasets - 1)
            avail_labels = list(self.class_img_idx[dataset_id].keys())
            if len(avail_labels) < self.num_ways:
                continue

            # n-way
            labels = random.sample(population=avail_labels, k=self.num_ways)

            # variable to store img idx
            batch = []
            for label in labels:
                batch.extend(random.sample(population=self.class_img_idx[dataset_id][label], k=self.num_samples_per_class))

            yield batch

    def __len__(self) -> int:
        total_combinations = sum(
            comb(len(class_dict), self.num_ways, exact=True)
            for class_dict in self.class_img_idx
            if class_dict and len(class_dict) >= self.num_ways
        )
        return int(total_combinations) if total_combinations > 0 else 100000
```

| Issue in master | Fix | Origin |
|---|---|---|
| `j = len(dataset)` instead of a cumulative sum, so global indices were wrong for the 3rd and later datasets of a `ConcatDataset` | `j += len(ds)` | Branch |
| Required a `ConcatDataset` (`.datasets`) | Also accepts a single dataset | Branch |
| Required a `.targets` attribute | Falls back to `ds[i][1]`; tensor labels converted with `.item()` | Branch |
| `random.sample(dict.keys(), ...)` deprecated in Python 3.9 and an error in 3.11 | Samples from `list(...)` | Branch |
| `__len__` referenced the undefined `self.label_list` / `self.batch` | Sum of C(#classes, num_ways) over usable datasets | Branch |
| (Branch) silent sampling with replacement | Restored master's without-replacement sampling; too-small classes raise a clear `ValueError` at start-up | Review |
| (Branch) infinite loop if no dataset had `num_ways` classes | Raises `ValueError` at start-up | Review |

---

## 5. Removed Files

**Origin:** [Branch] (commit `f7c939b`)

| File | Size | Used by |
|---|---|---|
| `meta_learning/maml/FcNet/Epoch_300.pt` | 26,287 bytes | `visualize_regression.ipynb` (`resume_maml = 300`) |
| `meta_learning/vampire2/FcNet/Epoch_100.pt` | 51,897 bytes | `visualize_regression.ipynb` (`resume_vampire2 = 100`) |

These are the authors' pretrained SineLine models. **The unchanged notebook raises `FileNotFoundError` on this fork** unless the models are retrained or restored with `git checkout master -- meta_learning/`. `*.pt` is now git-ignored (§6), so they can't be re-added without an ignore exception. This removal is intentional, because the fork targets custom tabular models.

---

## 6. Repository Configuration Files

### `.gitignore` [Branch]
Appended:
```gitignore
*.pt
*.csv
```
Effect: checkpoints, processed tensors and the raw CSV data are never committed. TensorBoard `events.*` files and `training_history.json` are **not** ignored; 38 such files are committed under `tabular/outputs/` (§8.8).

### `.gitattributes` [Branch]
A new, empty file (0 bytes) with no effect.

### `environment.yml` [Branch], new
```yaml
name: few_shot_meta_learning
channels:
  - defaults
dependencies:
  - python=3.10
  - numpy<2
  - scipy
  - pandas
  - scikit-learn
  - pip
  - pip:
    - torch>=1.8.1
    - torchvision
    - higher
    - tensorboard
```

### `condaenv.tgkel9t6.requirements.txt` [Branch], new
```text
torch --index-url https://download.pytorch.org/whl/cu124
torchvision --index-url https://download.pytorch.org/whl/cu124
higher
tensorboard
```

---

## 7. New Documentation Files

| File | Origin | Contents |
|---|---|---|
| `AGENTS.md` | Branch (commit `8294b2f`), Review (updates) | Architecture guide: hyper-net/base-net design, `higher` functional unrolling, template-method lifecycle, full directory map, tabular subsystem, engineering invariants, CLI runbook. The review updated Invariant 5 (checkpoints, validation cadence, fresh-run clean-up, SIMPA exemption) and the `test()` description, and added pointers to this document. |
| `HYPERPARAMETERS.md` | Branch, Review (updates) | Reference for every key in `tabular/config.json`, per-algorithm deep dive, applicability matrix, tuning guide. The review added `validate_every_n_epochs`, the BMAML bandwidth definition, LayerNorm initialisation, the speed-vs-monitoring tip and **§11 Behaviour Changes vs. Upstream `master`**. |
| `CHANGES_FROM_MASTER.md` | Review | This document. |
| `README.md` | Review | Unchanged authors' text, plus a short "Changes in this fork" section linking here. |

---

## 8. New Tabular Subsystem (`tabular/`)

**Origin:** [Branch], with [Review] changes marked. Everything here is new: none of it exists in `master`, and none of it is used by `main.py`. It meta-trains MAML, PLATIPUS, VAMPIRE, ABML and BMAML on wearable-sensor smoking-lapse data, using an `FcNet` or a `LogisticRegression` backbone, with fast-adaptation and cold-start evaluation.

### 8.1 File inventory

| Path | Lines | Purpose |
|---|---|---|
| `tabular/config.json` | 87 | Single source of truth for preprocessing, `fcnet`, `meta_training`, `fast_adaptation_eval` and `cold_start_eval` settings |
| `tabular/pipeline_order.json` | 247 | Manifest of 4 stages and 31 scripts, each with an `enabled` flag |
| `tabular/run_pipeline.py` | 381 | Headless runner: resolves the conda Python, runs enabled scripts as subprocesses, parses metrics, prints a comparison table |
| `tabular/metrics_utils.py` | 99 | `compute_metrics()`; delimited JSON output; persistence in `outputs/pipeline_results.json` |
| `tabular/dataloader/TabularDataLoader.py` | 193 | `load_config`, `resolve_device`, `find_best_checkpoint`, `TabularDataset`, `tabular_train_val_split`, `get_tabular_dataloader` |
| `tabular/dataloader/__init__.py` | 3 | Package exports |
| `tabular/scripts/Preprocessing.py` | 360 | Feature engineering and episode generation |
| `tabular/scripts/check_cuda.py` | 6 | Prints CUDA availability and device name |
| `tabular/experiments/fcnet/train_*_fcnet.py` | 5 × ~102 | Meta-training, FcNet backbone |
| `tabular/experiments/fcnet/eval_*_fcnet.py` | 5 × ~150 | Fast-adaptation evaluation, FcNet |
| `tabular/experiments/logistic_regression/train_*_lr.py` | 5 × ~98 | Meta-training, LogReg backbone |
| `tabular/experiments/logistic_regression/eval_*_lr.py` | 5 × ~145 | Fast-adaptation evaluation, LogReg |
| `tabular/experiments/no_adaptation/eval_cold_*_{fcnet,lr}.py` | 10 × ~146 | Cold-start (zero-shot) evaluation |
| `tabular/dashboard/app.py` | 338 | Zero-dependency HTTP server (default port 8050) |
| `tabular/dashboard/index.html` | 712 | Dashboard UI |
| `tabular/dashboard/app.js` | 1,387 | Dashboard logic: config editor, pipeline toggles, run/stop, log streaming, results |
| `tabular/dashboard/styles.css` | 1,423 | Dark-mode styling |
| `tabular/outputs/**` | 38 files | Committed run artifacts (§8.8) |
| `tabular/data/**` | — | Raw CSVs and processed `.pt` episodes (git-ignored) |

### 8.2 `tabular/config.json` (current, complete)
The `validate_every_n_epochs` key was added in the review; everything else comes from the branch.
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
    "num_hidden_units": [64, 64],
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
    "focal_loss": { "alpha": 0.65, "gamma": 3 }
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
    "focal_loss": { "alpha": 0.65, "gamma": 3 }
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
    "focal_loss": { "alpha": 0.65, "gamma": 3 }
  }
}
```
(The arrays and `focal_loss` objects are written on one line here for brevity; the file itself is multi-line.)

### 8.3 Data pipeline (`tabular/scripts/Preprocessing.py`)
- **31 features:** `time_sin`, `time_cos`, `is_sleeping`, `SEX_A`; continuous `AGEP_A`, `CIGNOW_A`, `TSLS_capped` (time since last smoke, clipped at 1,220 min), `HR`; 11 one-hot locations; 12 one-hot `EMP_STAT` codes.
- **Target:** `Did the user smoke in next 10 mins`, i.e. "Did The User Smoke?" shifted −1 step within each task.
- **HR alignment:** if mean HR > 40 bpm (absolute values), it subtracts a baseline: sleeping median + 15, or the awake 25th percentile.
- **Scaling:** `StandardScaler` on the continuous columns, fitted **only on the training tasks**. Test-case CSVs reuse that scaler.
- **Episodes:** 144 steps/day. The support set is Day 1 (144 rows) and the query set is the following days.
- **Splits (current data):** 6,177 train tasks / 1,090 validation tasks / 0 test tasks, each 144 support + 288 query rows. Two single-subject test cases have 144 support + 432 query rows. The positive rate is about 9.5 %.

### 8.4 Data loading (`tabular/dataloader/TabularDataLoader.py`)
| Function | Behaviour |
|---|---|
| `load_config(path=None)` | Reads `tabular/config.json` (or `{}` if missing) |
| `resolve_device(device)` | `"cuda"`/`"gpu"` → CUDA if available, else CPU with a warning |
| `find_best_checkpoint(log_dir, mode)` | `"last"`: highest `Epoch_N.pt`. `"best"`: `training_history.json` → `best_checkpoint`, else highest `val_acc` stored in the checkpoints, else the last epoch |
| `TabularDataset(path, device)` | Loads the list of episode dicts `{x_s, y_s, x_q, y_q, task_id}` |
| `tabular_train_val_split(eps_data)` | Maps support → `x_t, y_t` and query → `x_v, y_v` (squeezing the batch dimension) |
| `get_tabular_dataloader(path, shuffle, device)` | `DataLoader(batch_size=1)`, one episode per step |

### 8.5 Experiment scripts
**Training** (`train_<alg>_<arch>.py`) builds the algorithm config from `config.json`, uses `FocalLoss(alpha, gamma)` as `loss_function` and `tabular_train_val_split` as the split function, then calls `train(train_dl, val_dl)`. If test tasks exist (`test_frac > 0`) it also calls `test()`. Example config block (ABML + FcNet, current):
```python
    config = {
        'num_ways': 2,
        'k_shot': k_shot,
        'device': device,

        # Architecture
        'network_architecture': 'FcNet',
        'num_hidden_units': fcnet_cfg.get('num_hidden_units', (40, 40)),
        'activation': fcnet_cfg.get('activation', 'relu'),
        'dropout_rate': fcnet_cfg.get('dropout_rate', 0.0),
        'use_layernorm': fcnet_cfg.get('use_layernorm', False),
        'train_val_split_function': tabular_train_val_split,

        # Meta-learning hyperparameters
        'num_inner_updates': train_cfg.get('num_inner_updates', 5),
        'inner_lr': train_cfg.get('inner_lr', 0.01),
        'meta_lr': train_cfg.get('meta_lr', 1e-3),
        'KL_weight': train_cfg.get('KL_weight', 1e-6),
        'num_models': train_cfg.get('num_models', 4),
        'gamma_prior_concentration': train_cfg.get('gamma_prior_concentration', 1.0),
        'gamma_prior_rate': train_cfg.get('gamma_prior_rate', 0.01),
        'normal_prior_loc': train_cfg.get('normal_prior_loc', 0.0),
        'normal_prior_scale': train_cfg.get('normal_prior_scale', 1.0),

        # Gradient config
        'first_order': train_cfg.get('first_order', True),
        'train_flag': True,

        # Epochs and logging
        'resume_epoch': 0,
        'num_epochs': train_cfg.get('num_epochs', 20),
        'num_episodes': 100,
        'num_episodes_per_epoch': train_cfg.get('num_episodes_per_epoch', 1000),
        'minibatch': train_cfg.get('minibatch', 5),
        'minibatch_print': train_cfg.get('minibatch_print', 250),
        'validate_every_n_epochs': train_cfg.get('validate_every_n_epochs', 1),   # [Review] added to all 10 train scripts
        'logdir': LOG_DIR,
        'classification_threshold': train_cfg.get('classification_threshold', 0.5),

        # Loss
        'loss_function': FocalLoss(alpha=loss_cfg.get('alpha', 0.25), gamma=loss_cfg.get('gamma', 2.0))
    }
```

**Fast-adaptation evaluation** (`eval_<alg>_<arch>.py`):
1. Resolve the checkpoint with `find_best_checkpoint(log_dir, mode=checkpoint_mode)`.
2. Load the model.
3. For every `*_tasks.pt` test case, run `adaptation()` on the support set, then `prediction()` on the query set.
4. Average the softmax probabilities over the ensemble or samples, apply `classification_threshold`, and call `compute_metrics()`.

**Cold-start evaluation** (`eval_cold_<alg>_<arch>.py`) predicts the query set **without adaptation**:

| Algorithm | Cold-start prediction |
|---|---|
| MAML | `prediction(adapted_hyper_net=model["hyper_net"])`, i.e. the meta-initialisation |
| VAMPIRE, ABML | `prediction(model["hyper_net"])`, which samples `num_models` weight sets from the learned prior |
| BMAML | All particles of `EnsembleNet` |
| PLATIPUS | `mu_theta` repeated `num_models` times (deterministic, no sampling; see §11) |

### 8.6 Metrics (`tabular/metrics_utils.py`)
`compute_metrics(y_true, y_pred, test_case, task_id, model, architecture, mode, query_size)` builds a record with accuracy, macro-F1, weighted-F1, per-class precision/recall/F1 and the confusion matrix (percentages). It prints the record between delimiters so that `run_pipeline.py` can parse it from stdout:
```text
__METRICS_JSON_START__
{...record...}
__METRICS_JSON_END__
```
It also upserts the record into `tabular/outputs/pipeline_results.json`. `clear_all_metrics()` resets that file.

### 8.7 Orchestration
- **`run_pipeline.py`** CLI: `--config` (default `tabular/pipeline_order.json`), `--stage`, `--architecture`, `--model`, `--dry_run`, `--continue_on_error`. It finds a Python with torch (the current interpreter, or the conda env `few_shot_meta_learning` on Windows/Linux/macOS), runs enabled scripts and prints a comparison table.
- **`pipeline_order.json`** stages:
  1. `1_preprocessing` (1 script)
  2. `2_training` (10 scripts)
  3. `3_fast_adaptation_eval` (10 scripts)
  4. `4_cold_start_eval` (10 scripts)
- **Dashboard** (`python tabular/dashboard/app.py [--port 8050]`):
  - `GET /`, `/styles.css`, `/app.js`
  - `GET`/`POST` `/api/config` and `/api/pipeline`
  - `GET /api/results`, `/api/status`
  - `POST /api/results/clear`, `/api/run`, `/api/stop`
- **[Review]** The dashboard's meta-training form gained a **Validate Every N Epochs** field, read and saved as `meta_training.validate_every_n_epochs`. Without it, saving the config from the dashboard would drop the key:
  - `app.js`: default `validate_every_n_epochs: 1`; load into `#cfgTrainValidateEvery`; save via `getNumberVal('cfgTrainValidateEvery', 1)`.
  - `index.html`: new `<input type="number" id="cfgTrainValidateEvery" min="0" max="1000" value="1">`.

### 8.8 Committed run artifacts (`tabular/outputs/`)
38 files: `training_history.json` and TensorBoard event files for each of the 10 model folders (`logs_{maml,platipus,vampire,abml,bmaml}_{fcnet,lr}`), plus `pipeline_results.json`. Checkpoints (`*.pt`) are git-ignored.
- These artifacts were produced **before** the review's changes.
- Some histories contain entries from repeated runs: `logs_maml_fcnet` has 91 entries for 36 unique epochs, and `logs_vampire_fcnet` has 10 for 8. That was the stale-history bug fixed in §4.1.4.
- The event file names contain machine host names.

---

## 9. Unchanged Files

`main.py`, `RegressionDataset.py`, `visualize_regression.ipynb`, `LICENSE`. `README.md` keeps the authors' text, with only a link section added.

---

## 10. Verification Performed

All runs used the conda env `few_shot_meta_learning` (Python 3.10, CPU) and wrote only to temporary folders outside the repo.

| Check | Result |
|---|---|
| Every algorithm (MAML, VAMPIRE, ABML, BMAML, PLATIPUS) × backbone (FcNet, LogReg): 3-epoch training with `validate_every_n_epochs = 2` and `minibatch_print > num_episodes_per_epoch`, then `test()` | All pass. History holds epochs 1–3 once each; epochs 2 and 3 validated; best chosen among validated epochs; `test()` loads `Epoch_3.pt` |
| Validation off (`0`) | No validation, no `best_model.pt`, `"last"` → final epoch |
| Fresh run in a `logdir` holding a stale `Epoch_50.pt` | Stale files removed, unrelated files kept, `"last"` correct; resume keeps files |
| `test()` on an empty `logdir` | Clear `FileNotFoundError` |
| BMAML `get_kernel` vs `master`'s, for M = 2, 3, 8 | Kernel, gradient and `h` identical; no NaN on collapsed particles |
| ProtoNet + FcNet | Builds without `NameError` |
| LayerNorm init across all 5 algorithms | Scales = 1; 10/10 parameter groups receive gradient (was 2/10) |
| No-LayerNorm networks (FcNet, LogReg, CNN with affine BatchNorm) | Initialisation bit-identical to `master` |
| `EpisodeSampler`: 300 episodes over 3 concatenated datasets | 5 classes × 16 distinct samples, correct global indices; both error cases raise |
| `main.py` SineLine, MAML and BMAML train + test | Run normally |
| PLATIPUS before (committed branch) vs after (working tree), same seed, both backbones, both inits | **Bit-identical** validation losses (e.g. 0.0200541092 / 1.8013281649 / 0.0470283952 / 0.1560359617) |

**Experiments (tabular, 6 epochs × 500 episodes, 2 seeds):**

| Experiment | Result |
|---|---|
| PLATIPUS `mu_theta` init, FcNet | Branch init val loss 0.016–0.017 vs master 0.76–1.44; smoking-class F1 0.31–0.32 vs 0.22–0.26; macro-F1 ~0.52 vs 0.54–0.56 (master flags fewer rows) |
| PLATIPUS `mu_theta` init, LogReg | Branch init better on every metric: loss 0.018–0.023 vs 0.042–0.061; macro-F1 0.43–0.48 vs 0.35–0.40 |
| MAML LayerNorm | Off: 0.0164/0.0141. γ = 0: flat 0.0222 (no learning). γ = 1: 0.0161/0.0143 |
| BMAML kernel (prev branch / master / BMAML `org` / BMAML `med`) | FcNet ROC-AUC 0.898/0.898/0.898/0.899, PR-AUC 0.450/0.450/0.453/0.453. LogReg ROC-AUC 0.773/0.773/0.774/0.774. Differences within noise |
| Validation cost (from TensorBoard timestamps) | 7.5–9.3 % of each epoch at one validation per epoch |

---

## 11. Known Open Issues (Not Changed)

| Issue | Where | Notes |
|---|---|---|
| Models flag 43–60 % of rows as smoking (true rate ~9 %) | Tabular config | Threshold 0.35 combined with focal `alpha = 0.65`. Tune the threshold on validation data before trusting the F1 metrics |
| Accuracy-based `"best"` checkpoint selection | `save_epoch_checkpoint`, `find_best_checkpoint` | With ~9.5 % positives, "never smokes" scores ~90 % accuracy. Prefer `checkpoint_mode: "last"` (current) or select on loss/PR-AUC |
| BMAML kernel uses plain (not squared) distances, so `grad_kernel` isn't the exact gradient of the kernel; `h` isn't detached | `Bmaml.get_kernel` (inherited from `master`) | Fixing it (original BMAML `'org'`) changed results by ≤ 0.003 PR-AUC; kept as in master |
| PLATIPUS on SineLine hits `Loss is NaN` | Upstream `master` too (4/4 short runs) | Not caused by this fork; tabular PLATIPUS runs complete 100 epochs |
| PLATIPUS cold-start uses deterministic `mu_theta` | `eval_cold_platipus_*.py` | VAMPIRE/ABML cold-start sample from their priors |
| Test cases have 432 query rows (Days 2–4); train/val use 288 (Days 2–3) | Preprocessing | `AGENTS.md` describes Days 2–3 |
| HR baseline computed over all days of a test-case CSV | Preprocessing | Mild use of query-day information |
| Evaluation scripts never call `.eval()` | Tabular eval scripts | Harmless while `dropout_rate = 0` |
| `visualize_regression.ipynb` needs the removed checkpoints | §5 | Retrain or restore from `master` |
| SIMPA doesn't write `training_history.json` / `best_model.pt` | `Simpa.train` | Documented exemption |
