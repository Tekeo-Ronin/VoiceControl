import librosa
import torch

def preprocess_audio(path, sr=16000, n_mfcc=20):
    # Wczytaj plik dźwiękowy (np. .wav) i ustaw jego częstotliwość na 16 000 Hz
    y, _ = librosa.load(path, sr=sr)
    
    # Oblicz MFCC — to liczby, które opisują dźwięk w sposób zrozumiały dla komputera
    mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=n_mfcc)
    
    # Zamień MFCC na tensor PyTorch (czyli format danych, który rozumie model)
    mfcc = torch.tensor(mfcc.T, dtype=torch.float32)
    
    # Dodaj dodatkowy wymiar, żeby tensor miał kształt (1, czas, cechy)
    return mfcc.unsqueeze(0)
