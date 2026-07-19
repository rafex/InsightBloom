<template lang="pug">
.sandbox-loading
  .box-of-star1
    .star.star-position1
    .star.star-position2
    .star.star-position3
    .star.star-position4
    .star.star-position5
    .star.star-position6
    .star.star-position7
  .box-of-star2
    .star.star-position1
    .star.star-position2
    .star.star-position3
    .star.star-position4
    .star.star-position5
    .star.star-position6
    .star.star-position7
  .box-of-star3
    .star.star-position1
    .star.star-position2
    .star.star-position3
    .star.star-position4
    .star.star-position5
    .star.star-position6
    .star.star-position7
  .box-of-star4
    .star.star-position1
    .star.star-position2
    .star.star-position3
    .star.star-position4
    .star.star-position5
    .star.star-position6
    .star.star-position7
  .astronaut
    .head
      img.helmet-logo(src="@/assets/logo.svg" alt="")
    .arm.arm-left
    .arm.arm-right
    .body
      .panel
    .leg.leg-left
    .leg.leg-right
    .schoolbag
  p.loading-message {{ message }}
</template>

<script lang="ts">
export default {
  name: 'SandboxLoadingAnimation',
  props: {
    message: {
      type: String,
      default: 'Preparando tu ambiente de desarrollo...'
    }
  }
}
</script>

<style scoped>
/* Astronauta flotando entre estrellas mientras se aprovisiona el sandbox -- el casco muestra
   nuestro logo, que "gira" junto con el resto de la figura (misma animacion de rotacion). */
.sandbox-loading {
  position: relative;
  width: 100%;
  height: 320px;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.loading-message {
  position: absolute;
  bottom: 8px;
  left: 0;
  right: 0;
  z-index: 12;
  text-align: center;
  color: #4b5563;
  font-size: 0.95rem;
  margin: 0;
}

@keyframes snow {
  0% {
    opacity: 0;
    transform: translateY(0px);
  }

  20% {
    opacity: 1;
  }

  100% {
    opacity: 1;
    transform: translateY(280px);
  }
}

@keyframes astronaut {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

.box-of-star1,
.box-of-star2,
.box-of-star3,
.box-of-star4 {
  width: 100%;
  position: absolute;
  z-index: 10;
  left: 0;
  top: 0;
  transform: translateY(0px);
  height: 320px;
}

.box-of-star1 {
  animation: snow 5s linear infinite;
}

.box-of-star2 {
  animation: snow 5s -1.64s linear infinite;
}

.box-of-star3 {
  animation: snow 5s -2.30s linear infinite;
}

.box-of-star4 {
  animation: snow 5s -3.30s linear infinite;
}

.star {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background-color: #4f46e5;
  position: absolute;
  z-index: 10;
  opacity: 0.5;
}

.star-position1 {
  top: 20px;
  left: 5%;
}

.star-position2 {
  top: 60px;
  left: 20%;
}

.star-position3 {
  top: 30px;
  left: 38%;
}

.star-position4 {
  top: 70px;
  left: 58%;
}

.star-position5 {
  top: 10px;
  left: 74%;
}

.star-position6 {
  top: 50px;
  left: 86%;
}

.star-position7 {
  top: 15px;
  left: 96%;
}

.astronaut {
  width: 180px;
  height: 220px;
  position: absolute;
  z-index: 11;
  top: 30px;
  left: calc(50% - 90px);
  animation: astronaut 6s linear infinite;
}

.schoolbag {
  width: 72px;
  height: 108px;
  position: absolute;
  z-index: 1;
  top: calc(50% - 54px);
  left: calc(50% - 36px);
  background-color: #c7d2fe;
  border-radius: 36px 36px 0 0 / 22px 22px 0 0;
}

.head {
  width: 70px;
  height: 58px;
  position: absolute;
  z-index: 3;
  background: linear-gradient(to right, #e3e8eb 0%, #e3e8eb 50%, #fbfdfa 50%, #fbfdfa 100%);
  border-radius: 50%;
  top: 24px;
  left: calc(50% - 35px);
}

.helmet-logo {
  width: 42px;
  height: 36px;
  position: absolute;
  top: calc(50% - 18px);
  left: calc(50% - 21px);
  border-radius: 11px;
  background: linear-gradient(135deg, #15aece 0%, #0391bf 100%);
  padding: 5px;
  box-sizing: border-box;
  object-fit: contain;
}

.head:before {
  content: "";
  width: 9px;
  height: 18px;
  position: absolute;
  top: calc(50% - 9px);
  left: -3px;
  background-color: #618095;
  border-radius: 4px;
  box-shadow: 66px 0px 0px #618095;
}

.body {
  width: 61px;
  height: 72px;
  position: absolute;
  z-index: 2;
  border-radius: 30px / 15px;
  top: 76px;
  left: calc(50% - 30px);
  background: linear-gradient(to right, #e3e8eb 0%, #e3e8eb 50%, #fbfdfa 50%, #fbfdfa 100%);
}

.panel {
  width: 43px;
  height: 29px;
  position: absolute;
  top: 14px;
  left: calc(50% - 21px);
  background-color: #b7cceb;
}

.panel:before {
  content: "";
  width: 22px;
  height: 4px;
  position: absolute;
  top: 6px;
  left: 5px;
  background-color: #fbfdfa;
  box-shadow: 0px 6px 0px #fbfdfa, 0px 13px 0px #fbfdfa;
}

.panel:after {
  content: "";
  width: 6px;
  height: 6px;
  position: absolute;
  top: 6px;
  right: 5px;
  background-color: #fbfdfa;
  border-radius: 50%;
  box-shadow: 0px 10px 0px 1px #fbfdfa;
}

.arm {
  width: 58px;
  height: 22px;
  position: absolute;
  top: 87px;
  z-index: 2;
}

.arm-left {
  left: 22px;
  background-color: #e3e8eb;
  border-radius: 0 0 0 28px;
}

.arm-right {
  right: 22px;
  background-color: #fbfdfa;
  border-radius: 0 0 28px 0;
}

.arm-left:before,
.arm-right:before {
  content: "";
  width: 22px;
  height: 50px;
  position: absolute;
  top: -29px;
}

.arm-left:before {
  border-radius: 36px 36px 0px 86px / 36px 36px 0 79px;
  left: 0;
  background-color: #e3e8eb;
}

.arm-right:before {
  border-radius: 36px 36px 86px 0 / 36px 36px 79px 0;
  right: 0;
  background-color: #fbfdfa;
}

.arm-left:after,
.arm-right:after {
  content: "";
  width: 22px;
  height: 7px;
  position: absolute;
  top: -17px;
}

.arm-left:after {
  background-color: #6e91a4;
  left: 0;
}

.arm-right:after {
  right: 0;
  background-color: #b6d2e0;
}

.leg {
  width: 22px;
  height: 29px;
  position: absolute;
  z-index: 2;
  bottom: 50px;
}

.leg-left {
  left: 55px;
  background-color: #e3e8eb;
  transform: rotate(20deg);
}

.leg-right {
  right: 53px;
  background-color: #fbfdfa;
  transform: rotate(-20deg);
}

.leg-left:before,
.leg-right:before {
  content: "";
  width: 36px;
  height: 18px;
  position: absolute;
  bottom: -19px;
}

.leg-left:before {
  left: -14px;
  background-color: #e3e8eb;
  border-radius: 22px 0 0 0;
  border-bottom: 7px solid #6d96ac;
}

.leg-right:before {
  right: -14px;
  background-color: #fbfdfa;
  border-radius: 0 22px 0 0;
  border-bottom: 7px solid #b0cfe4;
}
</style>
