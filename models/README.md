Place the ONNX embedding assets here before starting the backend.

Default paths used by the application:

- `models/all-MiniLM-L6-v2/model.onnx`
- `models/all-MiniLM-L6-v2/tokenizer.json`

Override them with:

- `EMBEDDING_MODEL_PATH`
- `EMBEDDING_TOKENIZER_PATH`

Recommended starting model:

- `sentence-transformers/all-MiniLM-L6-v2`

The backend now fails fast when the ONNX model or tokenizer file is missing.
