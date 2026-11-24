import torch

import random
import typing

from scipy.special import comb

class EpisodeSampler(torch.utils.data.BatchSampler):
    """Sample data to form a classification task

    Args:
        data_source (Dataset): dataset to sample from
    """
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