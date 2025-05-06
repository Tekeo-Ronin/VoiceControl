# Ten kod ładuje model, który rozpoznaje komendy głosowe z plików dźwiękowych.
# Najpierw wczytuje listę komend, które model potrafi rozpoznać.
# Przechodzi przez folder „dataset” — w nim każdy podfolder to jedna komenda (np. „turn_on_browser”).
# W każdym podfolderze są pliki .wav — nagrania tej komendy.
# Dla każdego pliku:
# 1. Robi z dźwięku tzw. MFCC, czyli liczby opisujące dźwięk.
# 2. Podaje te liczby do modelu, żeby przewidział, co to za komenda.
# 3. Sprawdza, czy model zgadł poprawnie (czy przewidziana komenda = nazwa folderu).
# 4. Jeśli zgadł, zwiększa licznik poprawnych.
# Na końcu pokazuje, ile z wszystkich zgadywanek było trafnych — jako procent.


import os
import torch
import numpy as np
from utils import preprocess_audio

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

model = torch.jit.load("model/model.ptl")
model.eval()

dataset_path = "dataset"
correct = 0
total = 0

for class_name in sorted(os.listdir(dataset_path)):
    class_dir = os.path.join(dataset_path, class_name)
    if not os.path.isdir(class_dir):
        continue

    for file_name in os.listdir(class_dir):
        if not file_name.endswith(".wav"):
            continue

        file_path = os.path.join(class_dir, file_name)
        try:
            mfcc_tensor = preprocess_audio(file_path)

            mfcc_np = mfcc_tensor.squeeze(0).numpy()  # (20, 20)
            print(f"\n📂 {file_path}")
            print("Kształt MFCC:", mfcc_np.shape)

            print("Pierwsza ramka:", np.array2string(mfcc_np[0], precision=3, separator=", "))

            with torch.no_grad():
                output = model(mfcc_tensor)
                pred_idx = int(torch.argmax(output).item())
                predicted = COMMANDS[pred_idx]

            is_correct = predicted == class_name
            print(f"🔍 przewidziane: {predicted}, faktyczne: {class_name} {'✅' if is_correct else '❌'}")

            total += 1
            if is_correct:
                correct += 1

        except Exception as e:
            print(f"⚠️ Błąd podczas przetwarzania {file_path}: {e}")


print(f"\n📊 Dokładność: {correct}/{total} = {correct/total:.2%}" if total > 0 else "Nie znaleziono plików.")
