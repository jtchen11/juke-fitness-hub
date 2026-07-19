# face_service.py
import face_recognition
import numpy as np
import json
import os
from flask import Flask, request, jsonify
from flask_cors import CORS
import base64
from io import BytesIO
from PIL import Image
import cv2

app = Flask(__name__)
CORS(app)

# 存储人脸特征编码的字典 {user_id: [encoding1, encoding2, ...]}
face_encodings = {}

# 数据持久化文件
DATA_FILE = 'face_data.json'

def load_data():
    global face_encodings
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, 'r') as f:
            data = json.load(f)
            for key, value in data.items():
                face_encodings[key] = [np.array(enc) for enc in value]

def save_data():
    data = {}
    for key, value in face_encodings.items():
        data[key] = [enc.tolist() for enc in value]
    with open(DATA_FILE, 'w') as f:
        json.dump(data, f)

def base64_to_face_encoding(base64_str):
    # 去掉 data:image/png;base64, 前缀
    if ',' in base64_str:
        base64_str = base64_str.split(',')[1]

    try:
        image_data = base64.b64decode(base64_str)
        image = Image.open(BytesIO(image_data))
        # 转换为 RGB 数组
        image_array = np.array(image)

        # 检测人脸
        face_locations = face_recognition.face_locations(image_array)
        if len(face_locations) == 0:
            return None

        # 提取第一个人脸的特征编码
        face_encoding = face_recognition.face_encodings(image_array, face_locations)[0]
        return face_encoding.tolist()
    except Exception as e:
        print(f"处理图片出错: {e}")
        return None

@app.route('/api/face/register', methods=['POST'])
def register_face():
    try:
        data = request.json
        user_id = str(data.get('userId'))
        image_base64 = data.get('image')

        if not user_id or not image_base64:
            return jsonify({'success': False, 'message': '参数不完整'}), 400

        encoding = base64_to_face_encoding(image_base64)
        if encoding is None:
            return jsonify({'success': False, 'message': '未检测到人脸，请重新拍照'}), 400

        if user_id not in face_encodings:
            face_encodings[user_id] = []
        face_encodings[user_id].append(np.array(encoding))

        save_data()

        return jsonify({
            'success': True,
            'message': '人脸注册成功'
        })
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/face/verify', methods=['POST'])
def verify_face():
    try:
        data = request.json
        user_id = str(data.get('userId'))
        image_base64 = data.get('image')
        tolerance = float(data.get('tolerance', 0.5))

        if not user_id or not image_base64:
            return jsonify({'success': False, 'message': '参数不完整'}), 400

        if user_id not in face_encodings or len(face_encodings[user_id]) == 0:
            return jsonify({'success': False, 'message': '该用户尚未注册人脸'}), 400

        encoding = base64_to_face_encoding(image_base64)
        if encoding is None:
            return jsonify({'success': False, 'message': '未检测到人脸，请重新拍照'}), 400

        new_encoding = np.array(encoding)
        matched = False
        for registered_encoding in face_encodings[user_id]:
            distance = face_recognition.face_distance([registered_encoding], new_encoding)[0]
            if distance < tolerance:
                matched = True
                break

        if matched:
            return jsonify({
                'success': True,
                'message': '人脸匹配成功！签到完成 ✅',
                'matched': True
            })
        else:
            return jsonify({
                'success': False,
                'message': '人脸不匹配，请重试 ❌',
                'matched': False
            }), 400
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/face/delete', methods=['POST'])
def delete_face():
    try:
        data = request.json
        user_id = str(data.get('userId'))

        if user_id in face_encodings:
            del face_encodings[user_id]
            save_data()
            return jsonify({'success': True, 'message': '删除成功'})
        else:
            return jsonify({'success': False, 'message': '该用户未注册人脸'}), 404
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/face/check', methods=['GET'])
def check_face():
    user_id = request.args.get('userId')
    if not user_id:
        return jsonify({'success': False, 'message': '缺少userId参数'}), 400

    registered = user_id in face_encodings and len(face_encodings[user_id]) > 0
    return jsonify({
        'success': True,
        'registered': registered
    })

if __name__ == '__main__':
    load_data()
    print(f'已加载 {len(face_encodings)} 个用户的人脸数据')
    app.run(host='0.0.0.0', port=5001, debug=True)