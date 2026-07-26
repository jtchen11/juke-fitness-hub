import os
import base64
import io
import pickle
import traceback
from flask import Flask, request, jsonify
from flask_cors import CORS
import face_recognition
from PIL import Image
import numpy as np
import tempfile
app = Flask(__name__)
CORS(app)

DATA_FILE = "face_data.pkl"

if os.path.exists(DATA_FILE):
    with open(DATA_FILE, "rb") as f:
        face_data = pickle.load(f)
else:
    face_data = {}

def save_data():
    with open(DATA_FILE, "wb") as f:
        pickle.dump(face_data, f)

def decode_image(image_b64):
    print(f"[decode] len={len(image_b64)}, head={image_b64[:60]}")
    if "," in image_b64:
        image_b64 = image_b64.split(",")[1]
        print(f"[decode] after strip prefix, len={len(image_b64)}, head={image_b64[:60]}")
    img_data = base64.b64decode(image_b64)
    with tempfile.NamedTemporaryFile(delete=False, suffix='.jpg') as tmp:
        tmp.write(img_data)
        tmp_path = tmp.name
    try:
        img = face_recognition.load_image_file(tmp_path)
    finally:
        os.unlink(tmp_path)
    return img


@app.route("/api/face/register", methods=["POST"])
def register():
    data = request.get_json()
    user_id = data.get("userId")
    image_b64 = data.get("image")
    if not user_id or not image_b64:
        return jsonify({"success": False, "message": "缺少 userId 或 image"}), 400

    try:
        img_array = decode_image(image_b64)
        face_locations = face_recognition.face_locations(img_array)
        if len(face_locations) == 0:
            return jsonify({"success": False, "message": "未检测到人脸，请重拍"}), 400
        face_encodings = face_recognition.face_encodings(img_array, face_locations)
        if not face_encodings:
            return jsonify({"success": False, "message": "未检测到人脸，请正对摄像头"}), 400
        face_encoding = face_encodings[0]
        uid_key = str(user_id)
        face_data[uid_key] = face_encoding.tolist()
        save_data()
        print(f"[register] 注册成功 userId={uid_key}, face_data keys={list(face_data.keys())}")
        return jsonify({"success": True, "message": "人脸注册成功"})
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "message": f"注册失败：{str(e)}"}), 500

@app.route("/api/face/verify", methods=["POST"])
def verify():
    data = request.get_json()
    user_id = data.get("userId")
    image_b64 = data.get("image")
    tolerance = data.get("tolerance")
    if tolerance is None:
        tolerance = 0.5
    if not user_id or not image_b64:
        return jsonify({"success": False, "message": "缺少 userId 或 image"}), 400

    uid_key = str(user_id)
    stored_list = face_data.get(uid_key)
    if stored_list is None:
        return jsonify({"success": False, "message": "您尚未注册人脸，请先注册"}), 400

    try:
        img_array = decode_image(image_b64)
        face_locations = face_recognition.face_locations(img_array)
        if len(face_locations) == 0:
            return jsonify({"success": False, "message": "未检测到人脸"}), 400

        face_encodings = face_recognition.face_encodings(img_array, face_locations)
        if not face_encodings:
            return jsonify({"success": False, "message": "未检测到人脸，请正对摄像头"}), 400
        face_encoding = face_encodings[0]
        stored_encoding = np.array(stored_list)
        print(f"[verify] face_encoding shape={face_encoding.shape} dtype={face_encoding.dtype} first5={face_encoding[:5]}")
        print(f"[verify] stored_encoding shape={stored_encoding.shape} dtype={stored_encoding.dtype} first5={stored_encoding[:5]}")
        print(f"[verify] has_nan={np.any(np.isnan(stored_encoding))} has_inf={np.any(np.isinf(stored_encoding))}")
        # 如果存储数据有问题，清除后请重新注册
        if np.any(np.isnan(stored_encoding)) or np.any(np.isinf(stored_encoding)):
            del face_data[uid_key]
            save_data()
            return jsonify({"success": False, "message": "存储的人脸数据有异常，已清除，请重新注册"}), 400
        distance = float(np.linalg.norm(stored_encoding - face_encoding))
        print(f"[verify] face_distance={distance:.4f}, tolerance={tolerance}")
        matched = distance <= tolerance
        return jsonify({"success": True, "matched": matched})
    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "message": f"验证失败：{str(e)}"}), 500

@app.route("/api/face/delete", methods=["POST"])
def delete():
    data = request.get_json()
    user_id = data.get("userId")
    if not user_id:
        return jsonify({"success": False, "message": "缺少 userId"}), 400
    if user_id in face_data:
        del face_data[user_id]
        save_data()
        return jsonify({"success": True, "message": "删除成功"})
    else:
        return jsonify({"success": False, "message": "该用户未注册人脸"}), 404

@app.route("/api/face/check", methods=["GET"])
def check():
    user_id = request.args.get("userId")
    if not user_id:
        return jsonify({"success": False, "message": "缺少 userId"}), 400
    registered = user_id in face_data
    return jsonify({"success": True, "registered": registered})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)