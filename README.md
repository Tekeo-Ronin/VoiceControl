# Voice Control App

A mobile Android app and backend server for recognizing voice commands and executing actions on the phone.

## 📱 Mobile App (Android)

### What does the app do?
- Records your voice when you press the button.
- Trims silence and normalizes the recording.
- Sends the audio to the backend server.
- Receives a command from the backend and executes it:
    - Open camera
    - Turn flashlight on/off
    - Turn Bluetooth on/off
    - Open the browser

### How to run the app?
1. Open the project in **Android Studio**.
2. Connect an Android device or start an emulator.
3. Make sure the device has internet access (important!).
4. Build and run the app (`Run -> Run app`).
5. On the first launch, accept all required permissions (microphone, camera, Bluetooth).

---

## 💻 Backend (FastAPI + PyTorch)

### What does the backend do?
- Receives audio files from the app.
- Extracts audio features (MFCC).
- Sends the features to the PyTorch model (`model.ptl`).
- Returns the predicted command to the app.

### How to run the backend?
1. Make sure you have installed:
   - Python 3.9+
   - pip
2. Install all required libraries from `requirements.txt`:
```

pip install -r requirements.txt

```
3. Place the model file `model.ptl` in the `model/` folder.
4. Start the server:
```

uvicorn main\:app --host 0.0.0.0 --port 8000

```
5. Ensure your phone and computer are on the same local network (e.g., Wi-Fi).
6. In the app code (`AudioRecorder.kt`), set the correct local server address:
```

val url = "[http://192.168.x.x:8000/predict/](http://192.168.x.x:8000/predict/)"

```
Replace `192.168.x.x` with your computer’s local IP address.

---

## 📦 Project structure

```

.
├── android-app/
│   └── Android app code
├── backend/
│   ├── main.py (FastAPI server)
│   ├── utils.py (helper functions)
│   ├── requirements.txt (Python dependencies)
│   └── model/
│       └── model.ptl (PyTorch model)

```

---

## ⚙️ Additional info

- The app includes a test section (`Testowanie` in the menu) to test with sample audio files.
- If the backend is not running, the app will show a “Server error” message.
- If you want to train your own model, you need to save it as a `.ptl` TorchScript file.
```