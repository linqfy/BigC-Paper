"""Extrae la pose 3D (MediaPipe) de un tramo de video de baile.

Uso: python tools/extraer_pose.py <video> <inicio_s> <duracion_s> <fps> <salida.json> [preview.png]
Guarda por cuadro los 33 puntos en coordenadas de mundo (metros, centrados en la cadera).
"""
import json
import sys
from pathlib import Path

import cv2
import mediapipe as mp
from mediapipe.tasks.python import BaseOptions
from mediapipe.tasks.python import vision

MODEL = Path(__file__).resolve().parents[2] / "build" / "pose" / "pose_landmarker_heavy.task"
EDGES = [(11, 12), (11, 13), (13, 15), (12, 14), (14, 16), (11, 23), (12, 24), (23, 24),
         (23, 25), (25, 27), (24, 26), (26, 28), (0, 11), (0, 12)]


def crop_dancer(frame):
    """Recorta al bailarin (pixeles no verdes) con margen y lo agranda a 720 de alto."""
    import numpy as np
    b, g, r = frame[:, :, 0].astype(int), frame[:, :, 1].astype(int), frame[:, :, 2].astype(int)
    mask = ~((g > 90) & (g > r * 1.35) & (g > b * 1.35)) & ((r + g + b) > 30)
    ys, xs = np.nonzero(mask)
    if len(xs) < 50:
        return frame
    x1, x2, y1, y2 = xs.min(), xs.max(), ys.min(), ys.max()
    size = int(max(x2 - x1, y2 - y1) * 1.35)
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    h, w = frame.shape[:2]
    pad = np.full((h + 2 * size, w + 2 * size, 3), (0, 200, 0), dtype=np.uint8)
    pad[size:size + h, size:size + w] = frame
    crop = pad[cy + size - size // 2: cy + size + size // 2, cx + size - size // 2: cx + size + size // 2]
    return cv2.resize(crop, (720, 720))


def main():
    video, start, duration, fps, out = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), float(sys.argv[4]), sys.argv[5]
    preview = sys.argv[6] if len(sys.argv) > 6 else None
    options = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(MODEL)),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=float(__import__("os").environ.get("POSE_CONF", "0.3")),
        min_tracking_confidence=float(__import__("os").environ.get("POSE_CONF", "0.3")),
    )
    cap = cv2.VideoCapture(video)
    frames, thumbs = [], []
    with vision.PoseLandmarker.create_from_options(options) as landmarker:
        t = start
        while t < start + duration:
            cap.set(cv2.CAP_PROP_POS_MSEC, t * 1000)
            ok, frame = cap.read()
            if not ok:
                break
            frame = crop_dancer(frame)
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            result = landmarker.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), int(t * 1000))
            entry = {"t": round(t - start, 4), "world": None}
            if result.pose_world_landmarks:
                entry["world"] = [[p.x, p.y, p.z, p.visibility] for p in result.pose_world_landmarks[0]]
                image = [[p.x, p.y] for p in result.pose_landmarks[0]]
                entry["image"] = image
                if preview and len(thumbs) < 40:
                    for a, b in EDGES:
                        pa = (int(image[a][0] * frame.shape[1]), int(image[a][1] * frame.shape[0]))
                        pb = (int(image[b][0] * frame.shape[1]), int(image[b][1] * frame.shape[0]))
                        cv2.line(frame, pa, pb, (0, 0, 255), 3)
            if preview and len(thumbs) < 40 and len(frames) % max(1, int(fps / 2)) == 0:
                thumbs.append(cv2.resize(frame, (240, int(240 * frame.shape[0] / frame.shape[1]))))
            frames.append(entry)
            t += 1 / fps
    detected = sum(1 for f in frames if f["world"])
    Path(out).write_text(json.dumps({"video": video, "start": start, "fps": fps, "frames": frames}))
    print(f"{out}: {detected}/{len(frames)} cuadros con pose")
    if preview and thumbs:
        import numpy as np
        rows = [thumbs[i:i + 10] for i in range(0, len(thumbs), 10)]
        height = thumbs[0].shape[0]
        canvas = np.zeros((height * len(rows), 2400, 3), dtype=np.uint8)
        for r, row in enumerate(rows):
            for c, img in enumerate(row):
                canvas[r * height:(r + 1) * height, c * 240:c * 240 + img.shape[1]] = img[:height]
        cv2.imwrite(preview, canvas)


if __name__ == "__main__":
    main()
