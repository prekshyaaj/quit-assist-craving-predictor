# Build from the few_shot_meta_learning folder:
#   docker buildx build --platform linux/amd64 -f deploy/Dockerfile -t <image> .
FROM python:3.10-slim
WORKDIR /app
COPY deploy/requirements.txt deploy/requirements.txt
RUN pip install --no-cache-dir -r deploy/requirements.txt
COPY Maml.py MLBaseClass.py HyperNetClasses.py CommonModels.py _utils.py ./
COPY tabular/config.json tabular/config.json
COPY tabular/scripts/Preprocessing.py tabular/scripts/Preprocessing.py
COPY tabular/data/processed/dataset_prepro_routine_generated_meta.pt tabular/data/processed/
COPY tabular/outputs/logs_maml_fcnet/Epoch_100.pt tabular/outputs/logs_maml_fcnet/
COPY deploy/server.py deploy/server.py
ENV CHECKPOINT_EPOCH=100
CMD ["sh", "-c", "uvicorn deploy.server:app --app-dir /app --host 0.0.0.0 --port ${PORT:-8000}"]
