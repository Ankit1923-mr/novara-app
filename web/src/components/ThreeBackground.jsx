import { useEffect, useRef } from 'react'
import * as THREE from 'three'

export default function ThreeBackground({ variant = 'hero' }) {
  const mountRef = useRef(null)

  useEffect(() => {
    const container = mountRef.current
    if (!container) return

    // Scene setup
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(
      60,
      container.clientWidth / container.clientHeight,
      0.1,
      1000
    )
    camera.position.z = 24

    const renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
      powerPreference: 'high-performance'
    })
    renderer.setSize(container.clientWidth, container.clientHeight)
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    container.appendChild(renderer.domElement)

    // Group for rotation and mouse interaction
    const mainGroup = new THREE.Group()
    scene.add(mainGroup)

    // 1. Central Constellation Sphere (Nodes & Lines)
    const particleCount = variant === 'subtle' ? 120 : 280
    const geometry = new THREE.BufferGeometry()
    const positions = new Float32Array(particleCount * 3)
    const colors = new Float32Array(particleCount * 3)
    const radius = 10

    // Color palette: Emerald/Cyan/Amber neon
    const colorA = new THREE.Color('#10b981') // Emerald
    const colorB = new THREE.Color('#06b6d4') // Cyan
    const colorC = new THREE.Color('#f59e0b') // Amber/Gold

    const tempColor = new THREE.Color()

    for (let i = 0; i < particleCount; i++) {
      // Distribute points on sphere with slight jitter
      const phi = Math.acos(-1 + (2 * i) / particleCount)
      const theta = Math.sqrt(particleCount * Math.PI) * phi
      const r = radius + (Math.random() - 0.5) * 2.5

      const x = r * Math.cos(theta) * Math.sin(phi)
      const y = r * Math.sin(theta) * Math.sin(phi)
      const z = r * Math.cos(phi)

      positions[i * 3] = x
      positions[i * 3 + 1] = y
      positions[i * 3 + 2] = z

      // Gradient color mapping
      const ratio = Math.random()
      if (ratio < 0.55) {
        tempColor.copy(colorA).lerp(colorB, Math.random())
      } else {
        tempColor.copy(colorB).lerp(colorC, Math.random())
      }
      colors[i * 3] = tempColor.r
      colors[i * 3 + 1] = tempColor.g
      colors[i * 3 + 2] = tempColor.b
    }

    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

    // Point texture for soft glowing orbs
    const canvas = document.createElement('canvas')
    canvas.width = 64
    canvas.height = 64
    const ctx = canvas.getContext('2d')
    const grad = ctx.createRadialGradient(32, 32, 0, 32, 32, 32)
    grad.addColorStop(0, 'rgba(255, 255, 255, 1)')
    grad.addColorStop(0.3, 'rgba(16, 185, 129, 0.8)')
    grad.addColorStop(0.7, 'rgba(6, 182, 212, 0.2)')
    grad.addColorStop(1, 'rgba(0, 0, 0, 0)')
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, 64, 64)
    const particleTexture = new THREE.CanvasTexture(canvas)

    const material = new THREE.PointsMaterial({
      size: variant === 'subtle' ? 0.6 : 0.9,
      vertexColors: true,
      map: particleTexture,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    })

    const points = new THREE.Points(geometry, material)
    mainGroup.add(points)

    // 2. Dynamic Interconnecting Lines
    const lineMaterial = new THREE.LineBasicMaterial({
      color: 0x10b981,
      transparent: true,
      opacity: variant === 'subtle' ? 0.12 : 0.22,
      blending: THREE.AdditiveBlending,
    })

    // Connect close points
    const linePositions = []
    for (let i = 0; i < particleCount; i++) {
      for (let j = i + 1; j < particleCount; j++) {
        const dx = positions[i * 3] - positions[j * 3]
        const dy = positions[i * 3 + 1] - positions[j * 3 + 1]
        const dz = positions[i * 3 + 2] - positions[j * 3 + 2]
        const dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
        if (dist < 3.2) {
          linePositions.push(
            positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2],
            positions[j * 3], positions[j * 3 + 1], positions[j * 3 + 2]
          )
        }
      }
    }

    const lineGeometry = new THREE.BufferGeometry()
    lineGeometry.setAttribute('position', new THREE.Float32BufferAttribute(linePositions, 3))
    const lines = new THREE.LineSegments(lineGeometry, lineMaterial)
    mainGroup.add(lines)

    // 3. Ambient Outer Floating Dust Orbs
    const dustCount = variant === 'subtle' ? 60 : 160
    const dustGeo = new THREE.BufferGeometry()
    const dustPos = new Float32Array(dustCount * 3)
    for (let i = 0; i < dustCount; i++) {
      dustPos[i * 3] = (Math.random() - 0.5) * 50
      dustPos[i * 3 + 1] = (Math.random() - 0.5) * 40
      dustPos[i * 3 + 2] = (Math.random() - 0.5) * 30
    }
    dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3))
    const dustMat = new THREE.PointsMaterial({
      size: 0.45,
      color: 0x38bdf8,
      transparent: true,
      opacity: 0.4,
      blending: THREE.AdditiveBlending,
    })
    const dustPoints = new THREE.Points(dustGeo, dustMat)
    scene.add(dustPoints)

    // Mouse Tracking for smooth parallax
    let mouseX = 0
    let mouseY = 0
    let targetX = 0
    let targetY = 0

    const onMouseMove = (e) => {
      const rect = container.getBoundingClientRect()
      mouseX = ((e.clientX - rect.left) / container.clientWidth) * 2 - 1
      mouseY = -(((e.clientY - rect.top) / container.clientHeight) * 2 - 1)
    }

    window.addEventListener('mousemove', onMouseMove)

    // Resize Handler
    const handleResize = () => {
      if (!container) return
      camera.aspect = container.clientWidth / container.clientHeight
      camera.updateProjectionMatrix()
      renderer.setSize(container.clientWidth, container.clientHeight)
    }
    window.addEventListener('resize', handleResize)

    // Animation Loop
    let animationFrameId
    let clock = new THREE.Clock()

    const animate = () => {
      animationFrameId = requestAnimationFrame(animate)
      const elapsedTime = clock.getElapsedTime()

      // Smooth mouse follow
      targetX += (mouseX - targetX) * 0.05
      targetY += (mouseY - targetY) * 0.05

      mainGroup.rotation.y = elapsedTime * 0.12 + targetX * 0.5
      mainGroup.rotation.x = Math.sin(elapsedTime * 0.08) * 0.2 - targetY * 0.3

      // Gentle floating pulse
      points.rotation.y = elapsedTime * 0.04
      dustPoints.rotation.y = -elapsedTime * 0.03
      dustPoints.rotation.x = elapsedTime * 0.02

      renderer.render(scene, camera)
    }

    animate()

    return () => {
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('resize', handleResize)
      cancelAnimationFrame(animationFrameId)
      renderer.dispose()
      geometry.dispose()
      material.dispose()
      lineGeometry.dispose()
      lineMaterial.dispose()
      dustGeo.dispose()
      dustMat.dispose()
      if (container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement)
      }
    }
  }, [variant])

  return (
    <div
      ref={mountRef}
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
        zIndex: 0,
        overflow: 'hidden',
      }}
    />
  )
}
