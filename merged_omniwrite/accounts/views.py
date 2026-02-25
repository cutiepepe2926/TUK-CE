import logging
import traceback
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework_simplejwt.tokens import RefreshToken
from accounts.models import User

logger = logging.getLogger(__name__)

class RegisterView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        email = request.data.get('email')
        username = request.data.get('username')
        password = request.data.get('password')

        if not all([email, username, password]):
            return Response({"error": "모든 필드를 입력해 주세요."}, status=status.HTTP_400_BAD_REQUEST)

        if User.objects.filter(email=email).exists():
            return Response({"error": "이미 등록된 이메일입니다."}, status=status.HTTP_400_BAD_REQUEST)

        if User.objects.filter(username=username).exists():
            return Response({"error": "이미 존재하는 사용자명입니다."}, status=status.HTTP_400_BAD_REQUEST)

        try:
            user = User(username=username, email=email)
            user.set_password(password)
            user.save()
            return Response({"message": "회원가입 성공"}, status=status.HTTP_201_CREATED)
        except Exception as e:
            logger.error(f"회원가입 오류: {traceback.format_exc()}")
            return Response({"error": "회원가입 중 오류 발생"}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

class LoginView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        username = request.data.get("username")
        password = request.data.get("password")

        if not all([username, password]):
            return Response({"error": "아이디와 비밀번호를 입력해 주세요."}, status=status.HTTP_400_BAD_REQUEST)

        user = User.objects.filter(username=username).first()
        if not user or not user.check_password(password):
            return Response({"error": "유효하지 않은 아이디 또는 비밀번호입니다."}, status=status.HTTP_401_UNAUTHORIZED)

        refresh = RefreshToken.for_user(user)
        return Response({
            "access_token": str(refresh.access_token),
            "refresh_token": str(refresh)
        }, status=status.HTTP_200_OK)

class LogoutView(APIView):
    permission_classes = [AllowAny]  # ✅ 로그아웃은 인증 없이 가능하게 변경

    def post(self, request):
        refresh_token = request.data.get("refresh_token")
        if not refresh_token:
            return Response({"error": "로그아웃하려면 refresh_token이 필요합니다."}, status=status.HTTP_400_BAD_REQUEST)

        try:
            token = RefreshToken(refresh_token)
            token.blacklist()
            return Response({"message": "로그아웃 성공"}, status=status.HTTP_200_OK)
        except Exception:
            return Response({"error": "로그아웃 중 오류 발생"}, status=status.HTTP_400_BAD_REQUEST)


