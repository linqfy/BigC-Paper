"""Self-test of the Citadel animation interpreter and sampler: python -I tools/alexscaves/test_citadel_anim.py"""
import json
import math
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import citadel_anim as ca  # noqa: E402
import citadel_to_bbmodel as conv  # noqa: E402

MODEL = """
package com.github.alexmodguy.alexscaves.client.model;

public class TestModel extends AdvancedEntityModel<TestEntity> {
   private final AdvancedModelBox body;
   private final AdvancedModelBox tail;
   private final ModelAnimator animator;

   public TestModel() {
      this.texWidth = 64;
      this.texHeight = 64;
      this.body = new AdvancedModelBox(this);
      this.body.setRotationPoint(0.0F, 20.0F, 0.0F);
      this.body.setTextureOffset(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, 0.0F, false);
      this.tail = new AdvancedModelBox(this);
      this.tail.setRotationPoint(0.0F, 0.0F, 2.0F);
      this.body.addChild(this.tail);
      this.tail.setTextureOffset(0, 8).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F, 0.0F, false);
      this.animator = ModelAnimator.create();
      this.updateDefaultPose();
   }

   public void animate(IAnimatedEntity entity) {
      this.animator.update(entity);
      this.animator.setAnimation(TestEntity.ANIMATION_BITE);
      this.animator.startKeyframe(4);
      this.animator.rotate(this.body, (float)Math.toRadians(40.0), 0.0F, 0.0F);
      this.animator.endKeyframe();
      this.animator.setStaticKeyframe(2);
      this.animator.resetKeyframe(4);
   }

   public void setupAnim(TestEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
      this.resetToDefaultPose();
      this.animate(entity);
      float partialTick = ageInTicks - entity.f_19797_;
      float sit = entity.getSitProgress(partialTick);
      int mode = sit > 0.5F ? 1 : 0;
      switch (mode) {
         case 1:
            this.body.rotationPointY += 2.0F;
            break;
         default:
            this.body.rotationPointY += 0.0F;
      }
      this.swing(this.tail, 0.1F, 0.5F, false, 1.0F, 0.0F, ageInTicks, 1.0F);
      this.walk(this.body, 0.5F, 0.2F, true, 0.0F, 0.0F, limbSwing, limbSwingAmount);
      this.bob(this.body, 0.5F, 1.0F, false, limbSwing, limbSwingAmount);
      this.progressRotationPrev(this.tail, sit, (float)Math.toRadians(30.0), 0.0F, 0.0F, 1.0F);
   }
}
"""
ENTITY = """
public class TestEntity extends PathfinderMob implements IAnimatedEntity {
   public static final Animation ANIMATION_BITE = Animation.create(10);
}
"""


class InterpreterTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        root = cls.tmp.name
        model_dir = os.path.join(root, "com", "github", "alexmodguy", "alexscaves", "client", "model")
        entity_dir = os.path.join(root, "com", "github", "alexmodguy", "alexscaves", "server", "entity", "living")
        os.makedirs(model_dir)
        os.makedirs(entity_dir)
        with open(os.path.join(model_dir, "TestModel.java"), "w", encoding="utf-8") as handle:
            handle.write(MODEL)
        with open(os.path.join(entity_dir, "TestEntity.java"), "w", encoding="utf-8") as handle:
            handle.write(ENTITY)
        cls.root = root
        cls.parts, cls.roots, _, _ = conv.parse_model(MODEL, root)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def runtime(self):
        return ca.Runtime(self.root, "TestModel", self.parts)

    def test_procedural_terms_match_citadel(self):
        rt = self.runtime()
        pose = rt.run(ca.Context(7.0, 0.6, 0.6))
        limb = 7.0 * 0.6
        walk = -(math.cos(limb * 0.5) * 0.2 * 0.6)
        bob = math.sin(limb * 0.5) * 0.6 * 1.0 - 0.6 * 1.0
        self.assertAlmostEqual(pose["body"][0], walk, places=6)
        self.assertAlmostEqual(pose["body"][4], 20.0 + bob, places=6)
        self.assertAlmostEqual(pose["tail"][1], math.cos(7.0 * 0.1 + 1.0) * 0.5, places=6)

    def test_states_switch_and_progress(self):
        rt = self.runtime()
        pose = rt.run(ca.Context(0.0, states={"getSitProgress": 1.0}))
        self.assertAlmostEqual(pose["body"][4], 22.0)
        self.assertAlmostEqual(pose["tail"][0], math.radians(30.0), places=6)
        self.assertIn("getSitProgress", rt.state_calls)

    def test_model_animator_easing(self):
        rt = self.runtime()
        bite = ca.AnimRef("TestEntity", "ANIMATION_BITE")
        rx = [rt.run(ca.Context(float(t), animation=bite, animation_tick=t))["body"][0] for t in range(10)]
        self.assertAlmostEqual(rx[0], 0.0)
        self.assertAlmostEqual(rx[2], math.radians(40.0) * math.sin(0.5 * math.pi / 2), places=6)
        self.assertAlmostEqual(rx[4], math.radians(40.0), places=6)  # static keyframe holds
        self.assertAlmostEqual(rx[5], math.radians(40.0), places=6)
        self.assertAlmostEqual(rx[6], math.radians(40.0), places=6)  # reset starts easing back
        self.assertAlmostEqual(rx[8], math.radians(40.0) * (1 - math.sin(0.5 * math.pi / 2)), places=6)
        self.assertEqual(ca.entity_animations(self.root, "TestEntity"), {"ANIMATION_BITE": 10})

    def test_loop_length_covers_all_frequencies(self):
        length, seam = conv.loop_length([0.1, 0.3, 0.5])
        self.assertAlmostEqual(length, 2 * math.pi / 0.1, places=4)
        self.assertLess(seam, 1e-6)

    def test_prune_keeps_curve_within_tolerance(self):
        times = list(range(64))
        values = [(math.sin(t * 0.2) * 30, 0.0, 0.0) for t in times]
        keep = conv.prune(times, values, 0.4)
        self.assertLess(len(keep), len(times) * 3 // 4)
        for i in range(1, len(keep)):
            a, b = keep[i - 1], keep[i]
            for k in range(a, b + 1):
                expected = values[a][0] + (values[b][0] - values[a][0]) * (k - a) / (b - a)
                self.assertLessEqual(abs(expected - values[k][0]), 0.4)

    def test_build_animations(self):
        groups = {name: name for name in self.parts}
        animations, report = conv.build_animations(self.root, "TestModel", "test", self.parts, self.roots, groups)
        names = [a["name"] for a in animations]
        self.assertEqual(names, ["idle", "walk", "sit", "bite", "attack", "death"])
        bite = animations[names.index("bite")]
        self.assertEqual((bite["loop"], bite["length"], bite["override"]), ("once", 0.5, True))
        self.assertEqual(report["warnings"], [])


class OutputTest(unittest.TestCase):
    def test_non_finite_channels_fall_back_to_rest(self):
        poses = [{"b": (0.0, 0.0, 0.0, 0.0, math.inf, 0.0, 1.0, 1.0, math.nan)}, {"b": (0.1, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0)}]
        default = (0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        animators = conv.animators_from_samples("k", "a", {"b": "b"}, {"b": default}, [0, 1], poses, ["b"], 0.05)
        channels = {k["channel"] for k in animators["b"]["keyframes"]}
        self.assertEqual(channels, {"rotation"})

    def test_generated_models_are_finite(self):
        models = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources",
                              "bettermodel", "models")
        for name in sorted(os.listdir(models)):
            if not name.startswith("alexscaves_"):
                continue
            with open(os.path.join(models, name), encoding="utf-8") as handle:
                data = json.load(handle)
            for anim in data["animations"]:
                for animator in anim["animators"].values():
                    for key in animator["keyframes"]:
                        for point in key["data_points"]:
                            for axis in ("x", "y", "z"):
                                self.assertTrue(math.isfinite(float(point[axis])),
                                                f"{name} {anim['name']} {animator['name']} {point}")


if __name__ == "__main__":
    unittest.main()
