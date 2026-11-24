FROM python:3.12-slim AS whisper-build
RUN apt-get update && apt-get install -y --no-install-recommends git cmake build-essential curl ca-certificates
RUN git clone --depth 1 --branch v1.9.4 https://github.com/ggml-org/whisper.cpp /whisper.cpp
WORKDIR /whisper.cpp
RUN cmake -B build -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build --target whisper-server -j
RUN curl -L -o ggml-tiny.en.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin

FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY --from=whisper-build /whisper.cpp/build/bin/whisper-server /usr/local/bin/whisper-server
COPY --from=whisper-build /whisper.cpp/ggml-tiny.en.bin models/ggml-tiny.en.bin
COPY android_model_onnx android_model_onnx
COPY reference_embeddings.json vad.py whisper.py similarity.py main.py ./
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000}"]
