import logging
from fastapi import FastAPI, UploadFile, File
from fastapi.responses import JSONResponse
import torch
import librosa
import numpy as np
import io

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("voicecontrol")

app = FastAPI()
logger.info("Loading model...")
model = torch.jit.load("model/model.ptl")
model.eval()
logger.info("Model loaded successfully")

COMMANDS = [
    "okey_voiceapp",
    "turn_off_bluetooth",
    "turn_off_flashlight",
    "turn_on_bluetooth",
    "turn_on_browser",
    "turn_on_camera",
    "turn_on_flashlight",
    "unknown"
]

@app.post("/predict/")
async def predict(file: UploadFile = File(...)):
    try:
        logger.info(f"Received file: {file.filename}")

        contents = await file.read()
        logger.info(f"Read {len(contents)} bytes from uploaded file")

        audio_buffer = io.BytesIO(contents)
        logger.info("Loading audio with librosa")
        y, sr = librosa.load(audio_buffer, sr=16000)
        logger.info(f"Audio loaded: {len(y)} samples at {sr} Hz")

        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=20)
        logger.info(f"Computed MFCC shape: {mfcc.shape}")

        mfcc_tensor = torch.tensor(mfcc.T, dtype=torch.float32).unsqueeze(0)
        logger.info(f"MFCC tensor shape for model: {mfcc_tensor.shape}")

        with torch.no_grad():
            output = model(mfcc_tensor)
            pred_index = int(torch.argmax(output).item())
            predicted_label = COMMANDS[pred_index]

        logger.info(f"Model prediction: {predicted_label}")
        return JSONResponse(content={"command": predicted_label})

    except Exception as e:
        logger.error(f"Error during prediction: {str(e)}", exc_info=True)
        return JSONResponse(content={"error": str(e)}, status_code=500)
